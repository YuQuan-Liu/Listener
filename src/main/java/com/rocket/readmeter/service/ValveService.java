package com.rocket.readmeter.service;

import com.rocket.readmeter.dao.MeterMapper;
import com.rocket.readmeter.dao.ValveConfLogMapper;
import com.rocket.readmeter.dao.ValveLogMapper;
import com.rocket.readmeter.obj.Frame;
import com.rocket.readmeter.obj.ValveConfLog;
import com.rocket.readmeter.obj.Valvelog;
import com.rocket.utils.MybatisUtils;
import com.rocket.utils.StringUtil;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Rocket on 2018/6/28.
 */
public class ValveService {

    private static final Logger logger = LoggerFactory.getLogger(ReadService.class);

    /**
     * 根据valvelogid 获取 valvelog
     * @param pid
     * @return
     */
    public Valvelog getValveLogByID(int pid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();
        Valvelog valvelog = null;
        try{
            ValveLogMapper valveLogMapper = session.getMapper(ValveLogMapper.class);
            valvelog = valveLogMapper.getByID(pid);
        }finally {
            session.close();
        }

        return valvelog;
    }

    /**
     * 根据valvelogid 获取 其下的所有的ValveConfLog
     * @param valvelogid
     * @return
     */
    public List<ValveConfLog> getValveConfLog(int valvelogid){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();
        List<ValveConfLog> valveConfLogs = null;
        try{
            ValveConfLogMapper valveConfLogMapper = session.getMapper(ValveConfLogMapper.class);
            valveConfLogs = valveConfLogMapper.getValveConfLog(valvelogid);
        }finally {
            session.close();
        }

        return valveConfLogs;
    }

    /**
     * 更新表对应的阀门状态
     * @param mid
     * @param valvestatus
     */
    public void updateMeterValve(int mid, int valvestatus){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();
        try{
            MeterMapper meterMapper = session.getMapper(MeterMapper.class);
            meterMapper.updateMeterValve(mid,valvestatus);
            session.commit();
        }finally {
            session.close();
        }
    }

    /**
     * 更新阀门控制
     * @param valveConfLog
     */
    public void updateValveConfLog(ValveConfLog valveConfLog){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();
        try{
            ValveConfLogMapper valveConfLogMapper = session.getMapper(ValveConfLogMapper.class);
            valveConfLogMapper.updateValveConfLog(valveConfLog);
            session.commit();
        }finally {
            session.close();
        }
    }

    /**
     * 更新这次开关阀的结果
     * @param valvelogid
     * @param normal
     * @param error
     */
    public void updateValveLog(int valvelogid, int normal, int error){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();
        try{
            ValveLogMapper valveLogMapper = session.getMapper(ValveLogMapper.class);
            valveLogMapper.updateValveLog(valvelogid,normal,error);
            session.commit();
        }finally {
            session.close();
        }
    }

    /**
     * 对valvelogid 下的所有的表执行开关阀操作
     *
     * 现在的这个模式不适合使用session去控制开关阀
     * session是基于gprs的  开关阀是基于小区的。。。
     *
     * @param valvelogid
     */
    public void control(int valvelogid){
        logger.info("valveservice start control ; valvelogid:"+valvelogid);
        Valvelog valvelog = getValveLogByID(valvelogid);
        if(valvelog.getStatus() >= 100){
            logger.info("valvelog status = 100 ; valvelogid:"+valvelogid);
            return;
        }

        List<ValveConfLog> valveConfLogs = getValveConfLog(valvelogid);
        int control_cnt = valveConfLogs.size();
        int good = 0;
        int error = 0;
        for(int i = 0;i < control_cnt;i++){
            ValveConfLog valveConfLog = valveConfLogs.get(i);
            boolean finished = control_(valveConfLog);
            if (finished){
                good++;
            }else{
                error++;
            }
        }
        updateValveLog(valvelogid,good,error);
        logger.info("valveservice end control ; valvelogid:"+valvelogid+" ;good: "+good+" ;error: "+error);
    }

    /**
     * 对某个表执行开关阀操作
     * @param valveConfLog
     * @return
     */
    public boolean control_(ValveConfLog valveConfLog){

        Socket s = null;
        OutputStream out = null;
        InputStream in = null;
        int count = 0;
        byte[] data = new byte[1024];
        boolean finished = false;  //阀门开关是否成功  默认不成功
        String reason = "";  //开关失败原因
        byte seq = 0;   //服务器给集中器发送的序列号

        try {
            s = new Socket(valveConfLog.getIp(),valveConfLog.getPort());
            out = s.getOutputStream();
            in = s.getInputStream();

            //登录监听服务器
            boolean login = loginListener(s, out, in, valveConfLog.getGprsaddr());
            if(!login){
                finished = false;
                reason = "登陆监听异常";
                throw new RuntimeException(reason);
            }

            //序列号同步
            boolean seq_syn = synSEQ(s, out, in, seq, valveConfLog.getGprsaddr());
            if(!login){
                finished = false;
                reason = "序列号同步异常";
                throw new RuntimeException(reason);
            }

            //阀门控制
            for(int i = 0;i < 3 && !finished;i++){
                if(!finished){
                    seq++;
                    seq = (byte) (seq&0x0F);
                }

                boolean control_ack = sendControlFrame(s, out, in, seq, valveConfLog.getGprsaddr(), valveConfLog);
                if(!control_ack){  //没有收到集中器的收到指令确认  3s后重新发送指令
                    logger.info("给集中器发送控制指令 接收应答异常 " + i);
                    Thread.sleep(3000);
                    continue;
                }
                try {
                    s.setSoTimeout(30000);  //等待集中器返回数据  30s
                    byte[] deal = new byte[256];
                    int middle = 0;

                    int slave_seq = 20;  //接收过来的seq 取值范围为0~15  第一次肯定不相同
                    while ((count = in.read(data, 0, 256)) > 0) {

                        for(int k = 0;k < count;k++){
                            deal[middle+k] = data[k];
                        }
                        middle = middle + count;

                        int ret = checkFrame(deal, middle);
                        switch(ret){
                            case 0:  //数据不够  继续接收
                                break;
                            case -1:  //这一帧错误
                                middle = 0;  //重新开始接收
                                break;
                            case 1:  //这一帧正确处理
                                int slave_seq_ = deal[13] & 0x0F;
                                if(slave_seq != slave_seq_){
                                    slave_seq = slave_seq_;
                                    Frame readdata = new Frame(Arrays.copyOf(deal, middle));
                                    if(readdata.getFn() == 0x01){  //集中器收到发出去的指令
                                        finished = true;
                                    }
                                }else{
                                    //这条数据我已经收到过了  do nothing
                                }
                                //多帧时   为接收下一帧做准备
                                middle = 0;
                                break;
                        }
                        if(finished){  //跳出接收循环
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("接收集中器应答失败", e);
                    finished = false;
                    break;
                }
            }
            if(!finished){
                reason = "开关阀失败";
            }
        } catch (Exception e) {
            logger.error("control_ error ! valveconflog: "+valveConfLog.getPid(), e);
        } finally{
            try {
                if(in != null){
                    in.close();
                }
                if(out != null){
                    out.close();
                }
                if(s != null){
                    s.close();
                }
            } catch (Exception e) {
                logger.error("control_ error release resources ! valveconflog: "+valveConfLog.getPid(), e);
            }

            //更新 valveconflog db
            int result = finished?1:2;  //操作的结果
            valveConfLog.setResult(result);
            valveConfLog.setErrorreason(reason);
            updateValveConfLog(valveConfLog);
            if(finished){
                updateMeterValve(valveConfLog.getMeterid(), valveConfLog.getSwitchaction());
            }else{
                updateMeterValve(valveConfLog.getMeterid(), 2);
            }
        }
        return finished;
    }

    /**
     * 登录监听服务器
     * @param s
     * @param out
     * @param in
     * @param gprsaddr
     * @return
     */
    public boolean loginListener(Socket s, OutputStream out, InputStream in, String gprsaddr) {

        byte[] data = new byte[100];
        boolean good = false;
        byte[] gprsaddr_bytes = StringUtil.string2Byte(gprsaddr);

        Frame login = new Frame(0, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_LINE),
                Frame.AFN_LOGIN, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),
                (byte)0x01, gprsaddr_bytes, new byte[0]);

        try {
            out.write(login.getFrame());
            s.setSoTimeout(6000);  //6s内接收服务器的返回
            while((in.read(data, 0, 100)) > 0){
                break;
            }

            if(Frame.checkFrame(Arrays.copyOf(data, 17))){  //6s内收到监听返回  判断是否是ack
                Frame login_result = new Frame(Arrays.copyOf(data, 17));
                if(login_result.getFn() == 0x01){   //online  集中器在线
                    good = true;
                }
            }
        } catch (Exception e) {
            logger.error("loginListener error ! gprsaddr: "+gprsaddr, e);
        }
        return good;
    }



    /**
     * 确定集中器的服务器的序列号
     * @param s
     * @param out
     * @param in
     * @param seq
     * @param gprsaddr
     * @return
     */
    public boolean synSEQ(Socket s, OutputStream out, InputStream in, byte seq, String gprsaddr) {

        byte[] gprsaddr_bytes = StringUtil.string2Byte(gprsaddr);
        boolean seq_syn = false;
        byte[] data = new byte[100];
        //确定集中器的服务器的序列号
        for(int i = 0;i < 3 && !seq_syn;i++){
            byte[] framedata = new byte[0];

            Frame syn = new Frame(0, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND),
                    Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR | seq),
                    (byte)0x05, gprsaddr_bytes, framedata);
            try {
                out.write(syn.getFrame());
                s.setSoTimeout(10000);  //10s内接收服务器的返回
                while((in.read(data, 0, 100)) > 0){
                    break;
                }

                if(Frame.checkFrame(Arrays.copyOf(data, 17))){  //10s内收到监听返回  判断是否是ack
                    Frame ack = new Frame(Arrays.copyOf(data, 17));
                    if(ack.getFn() == 0x01){  //序列号同步
                        seq_syn = true;
                    }
                }

            } catch (Exception e) {
                logger.error("synSEQ error ! gprsaddr: "+gprsaddr, e);
            }
        }
        return seq_syn;
    }

    /**
     * 给集中器发送控制指令
     * @param s
     * @param out
     * @param in
     * @param seq
     * @param gprsaddr
     * @param valveConfLog
     * @return
     */
    public static boolean sendControlFrame(Socket s, OutputStream out, InputStream in, byte seq, String gprsaddr, ValveConfLog valveConfLog) {
        byte[] gprsaddr_bytes = StringUtil.string2Byte(gprsaddr);
        boolean ack = false;
        byte[] data = new byte[100];
        try {

            byte[] framedata = new byte[10];  //the data in the frame
            byte[] meteraddr = StringUtil.string2Byte(valveConfLog.getMeteraddr());
            framedata[0] = 0x10;
            for(int k= 1;k <= 7;k++){
                framedata[k] = meteraddr[6-(k-1)];
            }
            framedata[8] = 0x00;
            framedata[9] = 0x00;
            byte action = 0x03;  //默认开阀
            if(valveConfLog.getSwitchaction() == 0){
                action = 0x02;  //关阀
            }

            Frame controlFrame = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_FIRST),
                    Frame.AFN_CONTROL, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq),
                    action, gprsaddr_bytes, framedata);

            out.write(controlFrame.getFrame());
            s.setSoTimeout(10000);
            while((in.read(data, 0, 100)) > 0){
                break;
            }

            if(Frame.checkFrame(Arrays.copyOf(data, 17))){  //判断接收到的集中器的回应
                Frame ack_result = new Frame(Arrays.copyOf(data, 17));
                if(ack_result.getFn() == 0x01){  //集中器收到发出去的指令
                    ack = true;
                }
            }
        } catch (Exception e) {
            logger.error("sendControlFrame error ! gprsaddr: "+gprsaddr, e);
        }
        return ack;
    }

    /**
     * 检查从socket中接收到的数据    是否是一帧
     *
     * 从deal中查找帧  如果没有找到帧  则放弃。
     * 首先查找0x68  直到找到0x68为止
     *
     * @param deal
     * @param middle  从socket中接收到的byte数
     * @return 检查结果  -1~放弃   0~数据不够  1~帧正确
     */
    public int checkFrame(byte[] deal, int middle) {
        int frame_len = 0;  //帧的长度
        int ret = 0;   //检查结果  -1~放弃   0~数据不够  1~帧正确
        int header = 0;
        int data_len = 0;  //帧中数据长度

        if(header == 0){
            if(middle > 5){
                if(deal[0] == 0x68 && deal[5] == 0x68){
                    if(deal[1] == deal[3] && deal[2] == deal[4]){
                        data_len = (deal[1]&0xFF) | ((deal[2]&0xFF)<<8);
                        data_len = data_len >> 2;
                        header = 1;
                    }
                }
                if(header == 0){
                    //give up the data
                    ret = -1;
                }
            }
        }
        if(header == 1){
            if(middle >= (data_len + 8)){
                frame_len = data_len+8;

                byte cs = 0;
                for(int k = 6;k < frame_len-2;k++){
                    cs += deal[k];
                }
                if(cs == deal[frame_len-2] && deal[frame_len-1] == 0x16){
                    //这一帧OK
                    ret = 1;
                }else{
                    //这一帧有错误  放弃
                    ret = -1;
                }
            }else{
                //收到的数据还不够
                ret =  0;
            }
        }
        return ret;
    }


}
