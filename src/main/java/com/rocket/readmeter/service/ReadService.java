package com.rocket.readmeter.service;

import com.rocket.readmeter.Listener;
import com.rocket.readmeter.dao.GPRSMapper;
import com.rocket.readmeter.dao.MeterMapper;
import com.rocket.readmeter.dao.ReadLogMapper;
import com.rocket.readmeter.dao.ValveLogMapper;
import com.rocket.readmeter.obj.*;
import com.rocket.utils.MybatisUtils;
import com.rocket.utils.StringUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Rocket on 2018/6/28.
 */
public class ReadService {

    private static final Logger logger = LoggerFactory.getLogger(ReadService.class);

    /**
     * 根据pid 获取抄表记录 ReadLog
     * @param pid
     * @return
     */
    public Readlog getReadLogByID(int pid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        Readlog readlog = null;
        try {
            ReadLogMapper readLogMapper = session.getMapper(ReadLogMapper.class);
            readlog = readLogMapper.getByID(pid);
        } finally {
            session.close();
        }

        return readlog;
    }

    /**
     * 根据表ID 获取所在的GPRS
     * @param mid
     * @return
     */
    public GPRS getGPRSbyMID(int mid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        GPRS gprs = null;
        try{
            GPRSMapper gprsMapper = session.getMapper(GPRSMapper.class);
            gprs = gprsMapper.getGPRSbyMID(mid);
        }finally {
            session.close();
        }
        return gprs;
    }

    /**
     * 根据小区ID  获取小区下所有的GPRS
     * @param nid
     * @return
     */
    public List<GPRS> getGPRSsbyNID(int nid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        List<GPRS> gprsList = null;
        try{
            GPRSMapper gprsMapper = session.getMapper(GPRSMapper.class);
            gprsList = gprsMapper.getGPRSsbyNID(nid);
        }finally {
            session.close();
        }
        return gprsList;
    }

    /**
     * 根据表的ID 获取当前表Meter
     * @param mid
     * @return
     */
    public Meter getMeterbyID(int mid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        Meter meter = null;
        try{
            MeterMapper meterMapper = session.getMapper(MeterMapper.class);
            meter = meterMapper.getMeterbyID(mid);
        }finally {
            session.close();
        }
        return meter;
    }

    /**
     * 获取adminid 下所有的readlog
     * @param adminid
     * @param min_readlogid
     * @return
     */
    public List<Readlog> getAllNeighborReadlog(int adminid, int min_readlogid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        List<Readlog> readlogs = null;
        try{
            ReadLogMapper readLogMapper = session.getMapper(ReadLogMapper.class);
            readlogs = readLogMapper.getAllNeighborReadlog(adminid,min_readlogid);
        }finally {
            session.close();
        }
        return readlogs;
    }

    /**
     * 获取GPRS 下所有的采集器
     * @param gid
     * @return
     */
    public List<Collector> getCollectorsByGID(int gid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        List<Collector> collectors = null;
        try{
            MeterMapper meterMapper = session.getMapper(MeterMapper.class);
            collectors = meterMapper.getCollectorsByGID(gid);
        }finally {
            session.close();
        }
        return collectors;
    }

    /**
     * 更新readlog 状态
     * @param readlogid
     * @param finished
     * @param reason
     * @param result
     */
    public void updateReadLog(int readlogid,boolean finished, String reason, String result){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();
        try {
            ReadLogMapper readLogMapper = session.getMapper(ReadLogMapper.class);
            readLogMapper.updateReadLog(readlogid,finished,reason,result);
            session.commit();
        } finally {
            session.close();
        }
    }

    /**
     * 抄readlog id下的 表/小区/所有小区
     * @param readlogid
     */
    public void read(int readlogid){
        Readlog readlog = getReadLogByID(readlogid);
        if(readlog.getReadStatus() >= 100){
            return;
        }
        switch (readlog.getReadObject()) {
            case 1:  //抄单个小区
                int nid = readlog.getObjectId();
                readSingleNeighbor(nid, readlogid);
                break;
            case 2:  //抄管理员小的所有的小区
                int adminid = readlog.getObjectId();
                readNeighborsAdmin(adminid, readlogid);
                break;
            case 3:  //抄单个表
                int mid = readlog.getObjectId();
                readSingleMeter(mid, readlogid);
                break;
            default:
                break;
        }
    }

    /**
     * 抄单个表
     * @param mid
     * @param readlogid
     */
    public void readSingleMeter(int mid, int readlogid){

        //获取集中器对应的信息
        GPRS gprs = getGPRSbyMID(mid);
        Meter meter = getMeterbyID(mid);

        switch (gprs.getGprsprotocol()) {
            case 1:  //EG   XX
            case 4:  //D10 下的表 XX
                logger.error("EG & D10 not support ! readlogid : "+readlogid);
                break;
            case 2:  //188
            case 3:  //EG表  atom集中器
            case 5:  //188 v2
                readSingleMeter_(meter, gprs, readlogid);
                break;
        }

    }

    /**
     * 抄单个表 具体处理
     * @param meter
     * @param gprs
     * @param readlogid
     */
    private void readSingleMeter_(Meter meter, GPRS gprs, int readlogid){

        try {
            ConnectFuture future = Listener.connector.connect(new InetSocketAddress(gprs.getIp(),gprs.getPort()));
            future.awaitUninterruptibly();
            if(future.isConnected()){
                IoSession client = future.getSession();
                //连接成功  开始准备抄表！
                //抄表用到各个参数：
                client.setAttribute("meter",meter);
                client.setAttribute("gprs",gprs);
                client.setAttribute("readlogid",readlogid);
                client.setAttribute("state","login");  //抄表进行到哪个状态了
                client.setAttribute("seq",(byte)0);  //同步序列号
                client.setAttribute("syn_seq_retry",0);  //同步序列号尝试次数
                client.setAttribute("read_retry",0);  //抄表尝试次数
                client.setAttribute("read_type","single");  //单个表single  还是集中器all
                client.setAttribute("action","read");  //抄表read  开关阀valve
                client.setAttribute("frames",new ArrayList<Frame>());  //保存所有的抄表数据帧
                //抄海大表
                client.setAttribute("collectors",new ArrayList<Collector>());  //海大表时  所有采集器
                //抄小区时
                client.setAttribute("gprs_cnt",1);  //当前所抄小区 GPRS个数
                client.setAttribute("gprs_finish",new ConcurrentHashMap<String,String>());  //当前所抄小区 已完成的GPRS及结果
                //剩下的就去clientDataHandler中处理了！
            }else{
                //建立连接失败！ save to db
                updateReadLog(readlogid,true,"连接监听失败","连接监听失败");
                logger.error("read single meter error: connent to listener error : readlogid: "+readlogid);
            }
        } catch (Exception e) {
            updateReadLog(readlogid,true,"连接监听失败","连接监听失败");
            logger.error("read single meter error : readlogid: "+readlogid,e);
        }

    }

    /**
     * 抄单个小区
     * @param nid
     * @param readlogid
     */
    public void readSingleNeighbor(int nid, int readlogid){

        List<GPRS> gprsList = getGPRSsbyNID(nid);
        int gprs_cnt = gprsList.size();
        if(gprs_cnt == 0){
            //... 这个小区下没有GPRS
            logger.error("no gprs in neighbor ! readlogid :"+readlogid);
            updateReadLog(readlogid,true,"NO GPRS in neighbor","NO GPRS in neighbor");
        }else{
            ConcurrentHashMap<String,String> gprs_result = new ConcurrentHashMap<>();  //当前所抄小区 已完成的GPRS及结果
            for (GPRS gprs : gprsList) {
                readSingleGPRS(gprs, gprs_cnt, gprs_result, readlogid);
            }
        }

    }

    /**
     * 抄单个GPRS
     * @param gprs
     * @param gprs_cnt  小区下GPRS的个数
     * @param gprs_result
     * @param readlogid
     */
    private void readSingleGPRS(GPRS gprs, int gprs_cnt, ConcurrentHashMap<String,String> gprs_result, int readlogid){
        //抄海大表
        List<Collector> collectors = new ArrayList<>();
        if(gprs.getGprsprotocol() == 3){  //EG表  atom集中器
            //获取GPRS下所有采集器  并判断数量
            collectors = getCollectorsByGID(gprs.getPid());
            if(collectors.size() == 0){
                logger.error("no collectors in gprs ! readlogid :"+readlogid);
                updateReadLog(readlogid,true,"NO collectors in GPRS","NO collectors in GPRS");
                return;
            }
        }
        boolean error = false;
        try {
            ConnectFuture future = Listener.connector.connect(new InetSocketAddress(gprs.getIp(),gprs.getPort()));
            future.awaitUninterruptibly();
            if(future.isConnected()){
                IoSession client = future.getSession();
                //连接成功  开始准备抄表！
                //抄表用到各个参数：
                client.setAttribute("meter",null);
                client.setAttribute("gprs",gprs);
                client.setAttribute("readlogid",readlogid);
                client.setAttribute("state","login");  //抄表进行到哪个状态了
                client.setAttribute("seq",(byte)0);  //同步序列号
                client.setAttribute("syn_seq_retry",0);  //同步序列号尝试次数
                client.setAttribute("read_retry",0);  //抄表尝试次数
                client.setAttribute("read_type","all");  //单个表single  还是集中器all
                client.setAttribute("action","read");  //抄表read  开关阀valve
                client.setAttribute("frames",new ArrayList<Frame>());  //保存所有的抄表数据帧
                //抄海大表
                client.setAttribute("collectors",collectors);  //海大表时  所有采集器
                client.setAttribute("collectors_finish",new HashMap<String,String>());  //存储每个采集器的抄表结果
                client.setAttribute("collector_index",0);  //需要抄的采集器在collectors中的index
                //抄小区时
                client.setAttribute("gprs_cnt",gprs_cnt);  //当前所抄小区 GPRS个数
                client.setAttribute("gprs_finish",gprs_result);  //当前所抄小区 已完成的GPRS及结果
                //剩下的就去clientDataHandler中处理了！
            }else{
                //建立连接失败！ save gprs result
                error = true;
                gprs_result.put(gprs.getGprsaddr(),"连接监听失败");
                logger.error("read single gprs error: connent to listener error : readlogid: "+readlogid);
            }
        } catch (Exception e) {
            error = true;
            gprs_result.put(gprs.getGprsaddr(),"连接监听失败");
            logger.error("read gprs error : readlogid: "+readlogid,e);
        }

        if(error){  //连接监听的时候出错了
            if(gprs_cnt == 1){  //只有一个GPRS 更新DB
                updateReadLog(readlogid,true,"connent to listener error","connent to listener error");
            }else{
                if(gprs_cnt == gprs_result.size()){  //所有的GPRS都抄完了  保存结果
                    //将结果拼装到一起
                    String result = "";
                    for(Map.Entry<String,String> entry : gprs_result.entrySet()){
                        result = result +"<br/>"+ entry.getKey()+": "+entry.getValue();
                    }
                    updateReadLog(readlogid,true,result,result);
                }
            }
        }
    }

    /**
     * 抄管理员小的所有的小区
     * @param adminid
     * @param readlogid
     */
    public void readNeighborsAdmin(int adminid, int readlogid){
        List<Readlog> readlogs = getAllNeighborReadlog(adminid, readlogid);

        for(Readlog readlog : readlogs){
            readSingleNeighbor(readlog.getObjectId(), readlog.getPid());
        }

    }

}
