package com.rocket.readmeter;

import com.rocket.readmeter.obj.Frame;
import com.rocket.readmeter.obj.GPRS;
import com.rocket.readmeter.obj.Meter;
import com.rocket.readmeter.service.ReadService;
import com.rocket.utils.StringUtil;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class ClientDataHandler extends IoHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(ClientDataHandler.class);
	private ReadService readService = new ReadService();

	@Override
	public void exceptionCaught(IoSession session,Throwable cause){
		session.closeNow();
		logger.error("MINA error", cause);
	}
	
	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {

		int readlogid = (int)session.getAttribute("readlogid");
		int idle_cnt = session.getIdleCount(status);
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int seq = (int)session.getAttribute("seq");
		int syn_seq_retry = (int)session.getAttribute("syn_seq_retry");
		int read_retry = (int)session.getAttribute("read_retry");

		//判断当前状态
		String state = (String)session.getAttribute("state");
		switch (state){
			case "loginack":  //等待loginack超时
				readService.updateReadLog(readlogid,true,"登陆监听服务器等待超时","正常0;异常1");
				session.closeNow();
				break;
			case "synack":  //等待synack超时  尝试！
				if(syn_seq_retry < 3){  //尝试次数+1  继续同步序列号
					session.setAttribute("syn_seq_retry",syn_seq_retry+1);
					session.write(synFrame(gprs,seq));
				}else{  //同步序列号尝试3次 还是失败
					readService.updateReadLog(readlogid,true,"同步序列号尝试3次失败","正常0;异常1");
					session.closeNow();
				}
				break;
			case "readack":  //等待readack超时 尝试！
				if(read_retry < 3){
					//尝试次数+1  继续抄表
					session.setAttribute("read_retry",read_retry+1);
					session.write(readFrame(session));
				}else{
					//抄表尝试3次 还是失败
					readService.updateReadLog(readlogid,true,"抄表尝试3次失败","正常0;异常1");
					session.closeNow();
				}
				break;
			case "data":  //等待抄表数据  超时 idle_cnt
				if(idle_cnt > 10){
					//记录等待数据超时 TODO
					session.closeNow();  //抄表超时
				}
				break;
		}

	}

	@Override
	public void messageReceived(IoSession session,Object message){
		Frame frame = (Frame)message;

		String state = (String)session.getAttribute("state");

		switch(state){
			case "loginack":  //在等登陆监听服务器的ack
				login_ack(session, frame);
				break;
			case "synack":  //在等同步序列号的ack
				syn_ack(session, frame);
				break;
			case "readack":  //在等抄表指令的ack
				read_ack(session, frame);
				break;
			case "data":
				readdata(session, frame);
				break;
		}

	}

	@Override
	public void sessionOpened(IoSession session) throws Exception{
		//登陆监听服务器！
		Meter meter = (Meter)session.getAttribute("meter");
		GPRS gprs = (GPRS)session.getAttribute("gprs");

		session.setAttribute("state","loginack");
		session.write(loginFrame(gprs));

	}

	@Override
	public void sessionClosed(IoSession session) throws Exception{

	}

	/**
	 * 接收抄表 data
	 * @param session
	 * @param frame
     */
	private void readdata(IoSession session, Frame frame){
		logger.info("I am wait readdata, give me: "+frame);
		List<Frame> frames = (List<Frame>)session.getAttribute("frames");

		if(frame.getAfn() == 0x0B){
			frames.add(frame);

			//判断是不是单独帧 或 最后一帧  将结果保存到数据库
			int seq_sign = frame.getSeq() & 0x60;
			if(seq_sign == 0x60 || seq_sign == 0x40){
				//save the frames to DB
			}
		}

	}

	/**
	 * 等待抄表 ack
	 * @param session
	 * @param frame
     */
	private void read_ack(IoSession session, Frame frame) {
		logger.info("I am wait readack, give me: "+frame);

		Meter meter = (Meter)session.getAttribute("meter");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int readlogid = (int)session.getAttribute("readlogid");
		byte seq = (byte)session.getAttribute("seq");
		int read_retry = (int)session.getAttribute("read_retry");

		if(frame.getFn() == 0x01){
            //抄表指令OK  等待接收数据
            session.setAttribute("state","data");
        }else {
            //抄表指令失败  或者这个帧不是应答帧  集中器返回的指令有问题
			//这个地方不去尝试了  直接失败  如果是超时了才去尝试
			session.closeNow();
        }
	}

	/**
	 * 等待syn ack
	 * @param session
	 * @param frame
     */
	private void syn_ack(IoSession session, Frame frame) {
		logger.info("I am wait synack, give me: "+frame);

		Meter meter = (Meter)session.getAttribute("meter");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int readlogid = (int)session.getAttribute("readlogid");
		byte seq = (byte)session.getAttribute("seq");

		if(frame.getFn() == 0x01){
            //同步序列号OK  发送抄表指令
            session.setAttribute("state","readack");
            seq++;
            seq = (byte) (seq &0x0F);
            session.setAttribute("seq", seq);
            session.write(readFrame(session));
        }else {
            //同步序列号失败  或者这个帧不是应答帧
			//这个地方不去尝试了  直接失败  如果是超时了才去尝试
			session.closeNow();
		}
	}

	/**
	 * 等待login ack
	 * @param session
	 * @param frame
     */
	private void login_ack(IoSession session, Frame frame) {
		logger.info("I am wait loginack, give me: "+frame);

		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int readlogid = (int)session.getAttribute("readlogid");
		byte seq = (byte)session.getAttribute("seq");

		if(frame.getFn() == 0x01){
            //online 集中器在线  EGatom&188同步序列号 / 188v2发送抄表指令
            switch (gprs.getGprsprotocol()){
                case 2:  //EGatom
                case 3:  //188
					session.setAttribute("state","synack");
                    session.write(synFrame(gprs,seq));
                    break;
                case 5:  //188v2
					session.setAttribute("state","readack");
					seq++;
					seq = (byte) (seq &0x0F);
					session.setAttribute("seq", seq);
					session.write(readFrame(session));
                    break;
            }
        }else {
            //offline 集中器不在线  或者这个帧不是应答帧
            readService.updateReadLog(readlogid,true,"集中器不在线","正常0;异常1");
			session.closeNow();
        }
	}

	/**
	 * 同步序列号帧
	 * @param gprs
	 * @param seq
     * @return
     */
	public Frame synFrame(GPRS gprs,int seq){
		byte[] framedata = new byte[0];
		byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());
		Frame syn = new Frame(0, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND),
				Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR | seq),
				(byte)0x05, gprs_addr, framedata);
		return syn;
	}

	/**
	 * 登陆监听服务器帧
	 * @param gprs
	 * @return
     */
	public Frame loginFrame(GPRS gprs){
		byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());
		Frame login = new Frame(0, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_LINE),
				Frame.AFN_LOGIN, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),
				(byte)0x01, gprs_addr, new byte[0]);
		return login;
	}

	/**
	 * 抄表指令
	 * @param session
	 * @return
     */
	public Frame readFrame(IoSession session){
		Meter meter = (Meter)session.getAttribute("meter");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int readlogid = (int)session.getAttribute("readlogid");
		byte seq = (byte)session.getAttribute("seq");

		String action = (String)session.getAttribute("action");
		String read_type = (String)session.getAttribute("read_type");

		Frame frame = null;

		//开关阀
		if(action == "read"){
			switch (gprs.getGprsprotocol()){
				case 2:  //188
					frame = readFrame188(read_type, gprs, meter, seq);
					break;
				case 3:  //EG表
					frame = readFrameEG(read_type, gprs, meter, seq);
					break;
				case 5:  //188 v2
					frame = readFrame188v2(read_type, gprs, meter, seq);
					break;
			}
		}
		//抄表
		if(action == "valve"){
			switch (gprs.getGprsprotocol()){
				case 2:  //188
//					frame = controlFrame188(action, gprs, meter, seq);
					break;
			}
		}

		return frame;
	}

	/**
	 * 188协议对应的抄表指令
	 * @param read_type
	 * @param gprs
	 * @param meter
	 * @param seq
     * @return
     */
	private Frame readFrame188(String read_type, GPRS gprs, Meter meter, byte seq) {
		byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());
		byte[] framedata = new byte[11];

		switch (read_type){
            case "single":
				byte[] meteraddr = StringUtil.string2Byte(meter.getMeterAddr());
				framedata[0] = 0x10;
				for(int j= 1;j <= 7;j++){
					framedata[j] = meteraddr[6-(j-1)];
				}
				framedata[8] = 0x00;
				framedata[9] = 0x00;
				framedata[10] = 0x01;
                break;
            case "all":
				framedata[0] = 0x10;
				for(int i= 1;i <= 7;i++){
					framedata[i] = (byte) 0xFF;
				}
				framedata[8] = 0x00;
				framedata[9] = 0x00;
				framedata[10] = 0x01;
				break;
        }
		Frame read = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND),
				Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq),
				(byte)0x04, gprs_addr, framedata);
		return read;
	}

	/**
	 * 188v2协议对应的抄表指令
	 * @param read_type
	 * @param gprs
	 * @param meter
	 * @param seq
     * @return
     */
	private Frame readFrame188v2(String read_type, GPRS gprs, Meter meter, byte seq) {
		byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());
		byte[] framedata = null;

		switch (read_type){
			case "single":
				byte[] meteraddr = StringUtil.string2Byte(meter.getMeterAddr());
				byte[] cjqaddr = StringUtil.string2Byte(meter.getCollectorAddr());
				framedata = new byte[13];
				framedata[0] = 0x11;
				for(int j= 0;j < 5;j++){
					framedata[j+1] = cjqaddr[4-j];
				}
				for(int j= 0;j < 7;j++){
					framedata[j+6] = meteraddr[6-j];
				}
				break;
			case "all":
				framedata = new byte[1];
				framedata[0] = (byte) 0xFF;
				break;
		}
		Frame read = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND),
				Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq),
				(byte)0x04, gprs_addr, framedata);
		return read;
	}

	/**
	 * 海大协议对应的抄表指令
	 * @param read_type
	 * @param gprs
	 * @param meter
	 * @param seq
     * @return
     */
	private Frame readFrameEG(String read_type, GPRS gprs, Meter meter, byte seq) {
		byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());
		byte[] framedata = null;

		switch (read_type){
			case "single":
				framedata = new byte[4];
				framedata[0] = (byte) 0xAA;
				framedata[1] = (byte) (Integer.parseInt(meter.getCollectorAddr()) / 256);
				framedata[2] = (byte) Integer.parseInt(meter.getCollectorAddr());
				framedata[3] = (byte) Integer.parseInt(meter.getMeterAddr());//Byte.parseByte(meter.getMeterAddr());
				break;
			case "all":
				//这个需要一个采集器一个采集器的来 TODO...  这个晚会在处理

				break;
		}

		Frame read = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND),
				Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq),
				(byte)0x04, gprs_addr, framedata);
		return read;
	}

	/**
	 *
	 * @param read_type
	 * @param gprs
	 * @param meter
	 * @param seq
     * @return
     */
	private Frame controlFrame188(String read_type, GPRS gprs, Meter meter, byte seq, byte action) {
		byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());

		//TODO...
		return null;
	}

}
