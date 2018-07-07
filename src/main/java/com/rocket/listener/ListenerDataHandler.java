package com.rocket.listener;

import java.util.concurrent.ConcurrentHashMap;

import com.rocket.listener.obj.ListenerLog;
import com.rocket.listener.service.ListenerLogService;
import com.rocket.readmeter.obj.Frame;
import com.rocket.utils.RedisUtil;
import com.rocket.utils.StringUtil;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;


public class ListenerDataHandler extends IoHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ListenerDataHandler.class);

    private static final ConcurrentHashMap<String, IoSession> gprs = new ConcurrentHashMap<>(2048);
    private static final ConcurrentHashMap<String, IoSession> pc = new ConcurrentHashMap<>();
    private final ListenerLogService listenerLogService = new ListenerLogService();
    private final Jedis jedis = RedisUtil.getJedisConnection();

    @Override
    public void exceptionCaught(IoSession session,Throwable cause){
        session.closeNow();
        logger.error("MINA error", cause);
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {

        if(session.getIdleCount(status) == 1){
            String addr = (String) session.getAttribute("addr");
            String remote = session.getRemoteAddress().toString();
            logger.info("addr: "+ addr+";remote: "+remote+";idle!!!");
            session.closeNow();
        }

    }

    @Override
    public void sessionOpened(IoSession session) throws Exception{
        logger.info(session.getRemoteAddress().toString());
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception{
        if(session.getAttribute("online") == null){
            //son of bitch
        }else{
            String addr = (String) session.getAttribute("addr");
            String remote = session.getRemoteAddress().toString();
            logger.info("addr: "+ addr+";remote: "+remote+";closed!!!");
            session.setAttribute("online", false);
        }

    }

    @Override
    public void messageReceived(IoSession session,Object message){
        Frame frame = (Frame) message;
        String addr = frame.getAddrstr();
        byte from = frame.getDir();
        logger.info("from: " + from +";addr : "+ addr + ";frame : "+frame);
        if(session.getAttribute("online") == null){
            session.setAttribute("from", from);
            session.setAttribute("addr", addr);
        }
        session.setAttribute("online", true);

        if(from == 0x01){
            //from gprs
            fromGPRS(session, frame);
        }else{
            //from pc
            fromPC(session, frame);
        }
    }

    /**
     * 处理来自pc 服务器端的帧
     * @param session
     * @param frame
     */
    private void fromPC(IoSession session, Frame frame) {
        String frame_str = frame.toString();
        IoSession send = null;
        byte[] gprs_addr = StringUtil.string2Byte(frame.getAddrstr());

        switch(frame.getAfn()){
            case 0x02:  //链路接口
                switch(frame.getFn()){
                    case 0x01:  //登录
                        IoSession oldsession = pc.get(frame.getAddrstr());
                        if(oldsession != null && (boolean)oldsession.getAttribute("online")){
                            //这个GPRS已经在抄表了  新来的  你等会在来吧
                            session.write(new Frame(0,(byte)(Frame.ZERO|Frame.PRM_S_LINE),Frame.AFN_YES,(byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),(byte)0x02,gprs_addr,new byte[0]));
                            listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "1", "3", "WAIT",session.getRemoteAddress().toString()));
                        }else{  //确认
                            if(gprs.containsKey(frame.getAddrstr()) && (boolean)gprs.get(frame.getAddrstr()).getAttribute("online")){  //集中器在线
                                pc.put(frame.getAddrstr(), session);
                                jedis.setex("p_"+frame.getAddrstr(),180,session.getRemoteAddress().toString());
                                session.write(new Frame(0,(byte)(Frame.ZERO|Frame.PRM_S_LINE),Frame.AFN_YES,(byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),(byte)0x01,gprs_addr,new byte[0]));
                                listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "1", "3", "GPRS",session.getRemoteAddress().toString()));
                            }else{  //GPRS不在线
                                session.write(new Frame(0,(byte)(Frame.ZERO|Frame.PRM_S_LINE),Frame.AFN_YES,(byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),(byte)0x02,gprs_addr,new byte[0]));
                                listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "1", "3", "NOGPRS",session.getRemoteAddress().toString()));
                            }
                        }
                        break;
                    case 0x02:  //退出  PC没有实现
                        session.closeNow();
                        break;
                    case 0x03:  //心跳  PC不会有心跳
                        break;
                }
                break;
            case 0x00:  //确认否认
            case 0x03:  //设置参数
            case 0x04:  //控制命令
            case 0x0A:  //查询参数
            case 0x0B:  //实时数据
            case 0x0C:  //历史数据
                send = gprs.get(frame.getAddrstr());
                if(send != null && (boolean)send.getAttribute("online")){
                    send.write(frame);
                    listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "1", "3", frame_str,session.getRemoteAddress().toString()));
                }else{  //GPRS 不在线
                    session.write(new Frame(0,(byte)(Frame.ZERO|Frame.PRM_S_LINE),Frame.AFN_YES,(byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),(byte)0x02,gprs_addr,new byte[0]));
                }
                break;
        }
    }

    /**
     * 处理来自GPRS 的帧
     * @param session
     * @param frame
     */
    private void fromGPRS(IoSession session, Frame frame) {
        String frame_str = frame.toString();
        IoSession send = null;
        byte[] gprs_addr = StringUtil.string2Byte(frame.getAddrstr());

        switch(frame.getAfn()){
            case 0x02:  //链路接口
                switch(frame.getFn()){
                    case 0x01:  //登录  集中器没有实现
                        gprs.put(frame.getAddrstr(), session);
                        jedis.setex("g_"+frame.getAddrstr(),180,session.getRemoteAddress().toString());
                        //确认
                        session.write(new Frame(0,(byte)(Frame.ZERO|Frame.PRM_S_LINE),Frame.AFN_YES,(byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),(byte)0x01,gprs_addr,new byte[0]));
                        listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "0", "1", "",session.getRemoteAddress().toString()));
                        break;
                    case 0x02:  //退出  集中器没有实现
                        session.closeNow();
                        break;
                    case 0x03:  //心跳
                        gprs.put(frame.getAddrstr(), session);
                        jedis.setex("g_"+frame.getAddrstr(),180,session.getRemoteAddress().toString());
                        session.write(new Frame(0,(byte)(Frame.ZERO|Frame.PRM_S_LINE),Frame.AFN_YES,(byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR | (frame.getSeq()&0x0F)),(byte)0x01,gprs_addr,new byte[0]));
                        listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "0", "4", "",session.getRemoteAddress().toString()));
                        break;
                }
                break;
            case 0x00:  //确认否认
            case 0x03:  //设置参数  由PC发出  集中器返回确认否认帧
            case 0x04:  //控制命令  由PC发出  集中器返回确认否认帧
            case 0x0A:  //查询参数  由PC发出  集中器返回数据帧
            case 0x0C:  //历史数据   由PC发出  集中器返回数据帧
            case 0x0F:  //抄全部表时   集中器上报的抄表进行中的帧  防止抄表定时超时 9s一次
                //集中器发送过来的数据  发给PC
                send = pc.get(frame.getAddrstr());
                if(send != null && (boolean)send.getAttribute("online")){
                    send.write(frame);
                    listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "0", "3", frame_str,session.getRemoteAddress().toString()));
                }
                break;
            case 0x0B:  //实时数据   由PC发出  集中器返回数据帧
                //发送应答帧  ，将数据帧判断  发送给抄表程序
                byte slave_seq_ = (byte) (frame.getSeq() & 0x0F);
                Frame data_ack = new Frame(0, (byte)(Frame.ZERO),
                        Frame.AFN_YES, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR | slave_seq_),
                        (byte)0x01, gprs_addr, new byte[0]);
                session.write(data_ack);

                send = pc.get(frame.getAddrstr());
                if(send != null && (boolean)send.getAttribute("online")){
                    send.write(frame);
                    listenerLogService.insertListenerLog(new ListenerLog(frame.getAddrstr(), "0", "3", frame_str,session.getRemoteAddress().toString()));
                }
                break;
        }
    }

}
