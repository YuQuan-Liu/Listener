package com.rocket.readmeter;

import com.rocket.readmeter.obj.*;
import com.rocket.readmeter.service.ReadService;
import com.rocket.utils.StringUtil;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ClientDataHandler extends IoHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(ClientDataHandler.class);
	private ReadService readService = new ReadService();

	@Override
	public void exceptionCaught(IoSession session,Throwable cause){

		ConcurrentHashMap<String,String> gprs_finish = (ConcurrentHashMap<String, String>) session.getAttribute("gprs_finish");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		gprs_finish.put(gprs.getGprsaddr(),"抄表异常");
		logger.error("MINA error", cause);
		session.closeNow();
	}
	
	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {

		int idle_cnt = session.getIdleCount(status);
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		byte seq = (byte)session.getAttribute("seq");
		int syn_seq_retry = (int)session.getAttribute("syn_seq_retry");
		int read_retry = (int)session.getAttribute("read_retry");
		ConcurrentHashMap<String,String> gprs_finish = (ConcurrentHashMap<String, String>) session.getAttribute("gprs_finish");
		String read_type = (String) session.getAttribute("read_type");
		int data_timeout_cnt = 0;
		//如果是单个表  超时时间是20s[eg & 188 & 188v2]
		//如果是全部表  超时时间 120s[eg]  120s[188 & 188v2][每9s有Fake帧]
		switch (read_type){
			case "single":
				data_timeout_cnt = 2;
				break;
			case "all":
				switch (gprs.getGprsprotocol()){
					case GPRS.GPRSPROTOCOL_EGATOM:  //EGatom
						data_timeout_cnt = 12;
						break;
					case GPRS.GPRSPROTOCOL_188:  //188
					case GPRS.GPRSPROTOCOL_188V2:  //188v2
						data_timeout_cnt = 30;
						break;
				}
				break;
		}

		//判断当前状态
		String state = (String)session.getAttribute("state");
		switch (state){
			case "loginack":  //等待loginack超时
				gprs_finish.put(gprs.getGprsaddr(),"登陆监听超时");
				session.closeNow();
				break;
			case "synack":  //等待synack超时  尝试！
				if(syn_seq_retry < 3){  //尝试次数+1  继续同步序列号
					session.setAttribute("syn_seq_retry",syn_seq_retry+1);
					session.write(synFrame(gprs,seq));
				}else{  //同步序列号尝试3次 还是失败
					gprs_finish.put(gprs.getGprsaddr(),"同步序列号尝试3次失败");
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
					gprs_finish.put(gprs.getGprsaddr(),"抄表尝试3次失败");
					session.closeNow();
				}
				break;
			case "data":  //等待抄表数据  超时 idle_cnt
				//如果是单个表  超时时间是20s[eg & 188 & 188v2]
				//如果是全部表  超时时间 120s[eg]  30s[188 & 188v2][每9s有Fake帧]
				if(idle_cnt >= data_timeout_cnt){
					//记录等待数据超时  先不去尝试抄表了 TODO
					gprs_finish.put(gprs.getGprsaddr(),"等待抄表数据失败");
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

	}

	@Override
	public void sessionClosed(IoSession session) throws Exception{
		//在session 关闭的时候 处理：readService.updateReadLog 这次抄表结束
		ConcurrentHashMap<String,String> gprs_finish = (ConcurrentHashMap<String, String>) session.getAttribute("gprs_finish");
		int readlogid = (int)session.getAttribute("readlogid");

		int gprs_cnt = (int)session.getAttribute("gprs_cnt");
		int gprs_finish_cnt = gprs_finish.size();
		if(gprs_cnt == gprs_finish_cnt){
			//将结果拼装到一起
			String result = "";
			for(Map.Entry<String,String> entry : gprs_finish.entrySet()){
				result = result +"<br/>"+ entry.getKey()+":"+entry.getValue();
			}
			readService.updateReadLog(readlogid,true,result,result);
		}

	}

	/**
	 * 接收抄表 data
	 * @param session
	 * @param frame
     */
	private void readdata(IoSession session, Frame frame){
		logger.info("I am wait readdata, give me: "+frame);
		List<Frame> frames = (List<Frame>)session.getAttribute("frames");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		ConcurrentHashMap<String,String> gprs_finish = (ConcurrentHashMap<String, String>) session.getAttribute("gprs_finish");

		if(frame.getAfn() == 0x0B){
			frames.add(frame);
			//判断是不是单独帧 或 最后一帧  将结果保存到数据库
			int seq_sign = frame.getSeq() & 0x60;
			if(seq_sign == 0x60 || seq_sign == 0x20){
				String result = "";  //保存数据的结果
				switch (gprs.getGprsprotocol()){
					case GPRS.GPRSPROTOCOL_EGATOM:  //EGatom
						//判断是不是最后一个采集器 如果是最后一个采集器结束
						result = saveReadData(session);
						int collector_index = (int)session.getAttribute("collector_index");
						List<Collector> collectors = (List<Collector>)session.getAttribute("collectors");
						HashMap<String,String> collectors_finish = (HashMap<String, String>) session.getAttribute("collectors_finish");
						collectors_finish.put(collectors.get(collector_index).getColAddr()+"",result);
						collector_index = collector_index + 1; //下一个采集器 index
						if(collector_index == collectors.size()){  //这个是最后一个采集器  结束
							//将采集器的所有的结果拼接起来
							String collector_results = "";
							for(Map.Entry<String,String> entry : collectors_finish.entrySet()){
								collector_results = collector_results +"&nbsp;&nbsp;<br/>"+ entry.getKey()+": "+entry.getValue();
							}
							gprs_finish.put(gprs.getGprsaddr(),collector_results);
							session.closeNow();
						}else{  //下一个采集器
							session.setAttribute("collector_index",collector_index);
							session.setAttribute("state","readack");
							frames.clear();  //清空frames
							session.write(readFrame(session));
						}
						break;
					case GPRS.GPRSPROTOCOL_188:  //188
						result = saveReadData(session);
						gprs_finish.put(gprs.getGprsaddr(),result);
						session.closeNow();
						break;
					case GPRS.GPRSPROTOCOL_188V2:  //188v2
						//判断当前帧序号与总帧数的关系  如果当前帧序号==总帧数 保存
						byte[] frame_bytes = frame.getFrame();
						int frame_all = (frame_bytes[15] & 0xFF) | ((frame_bytes[16] & 0xFF) << 8);
						int frame_count = (frame_bytes[17] & 0xFF) | ((frame_bytes[18] & 0xFF) << 8);
						logger.info("188v2  frame_all: "+frame_all+"; frame_count: "+frame_count);
						if(frame_all == frame_count){
							result = saveReadData(session);
							gprs_finish.put(gprs.getGprsaddr(),result);
							session.closeNow();
						}
						break;
				}

			}
		}

	}

	/**
	 * 保存抄表数据到DB
	 * @param session
	 * @return
     */
	private String saveReadData(IoSession session){
		logger.info("save read data to db !");
		GPRS gprs = (GPRS) session.getAttribute("gprs");

		HashMap<String,Integer> saveresult = new HashMap<>();
		saveresult.put("good",0);
		saveresult.put("error",0);
		//根据不同的协议保存表的数据
		switch (gprs.getGprsprotocol()){
			case GPRS.GPRSPROTOCOL_EGATOM:  //EGatom
				saveresult = saveReadDataEG(session);
				break;
			case GPRS.GPRSPROTOCOL_188:  //188
			case GPRS.GPRSPROTOCOL_188V2:  //188v2
				saveresult = saveReadData188(session);
				break;
		}

		return "正常："+saveresult.get("good")+"; 异常："+saveresult.get("error");
	}

	public HashMap<String,Integer> saveReadDataEG(IoSession session){
		HashMap<String,Integer> saveresult = null;
		String read_type = (String) session.getAttribute("read_type");
		switch (read_type){
			case "single":
				saveresult = saveReadDataEGSingle(session);
				break;
			case "all":
				saveresult = saveReadDataEGAll(session);
				break;
		}

		return saveresult;
	}

	/**
	 * 保存EG协议的表到DB  抄单个表
	 * @param session
	 * @return
	 */
	public HashMap<String,Integer> saveReadDataEGSingle(IoSession session){
		HashMap<String,Integer> saveresult = new HashMap<>();
		List<Frame> frames = (List<Frame>)session.getAttribute("frames");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int readlogid = (int)session.getAttribute("readlogid");
		Meter meter = (Meter) session.getAttribute("meter");

		HashMap<String,MeterRead> meterreads = new HashMap<>();
		HashMap<String,String> goodmeters = new HashMap<>();
		HashMap<String,String> errormeters = new HashMap<>();

		for(Frame frame : frames){
			byte[] frame_bytes = frame.getFrame();

			if(frame_bytes[18] == (byte) Integer.parseInt(meter.getMeterAddr())){
				int meterread = -1;
				int meteraddr = 1;
				byte meterstatus = 1;
				String remark = "";

				meteraddr = frame_bytes[18]&0xFF;
				meterread = frame_bytes[18+1]&0xFF;
				meterread = meterread << 8;
				meterread = meterread|(frame_bytes[18+2]&0xFF);
				String numstr = Integer.toHexString(meterread);

				if(numstr.equals("aaaa")){
					meterread = -1;
					meterstatus = 2;
					remark = "aaaa";
					errormeters.put(meteraddr+"","");
				}else{
					if(numstr.equals("bbbb")){
						meterread = -1;
						meterstatus = 3;
						remark = "bbbb";
						errormeters.put(meteraddr+"","");
					}else{
						meterstatus = 1;
						remark = "";
						try {
							meterread = Integer.valueOf(numstr);
							goodmeters.put(meteraddr+"","");
						} catch (NumberFormatException e) {
							errormeters.put(meteraddr+"","");
							meterread = -1;
							remark = e.getMessage();
							logger.error("numstr to meterread error ! meterread: "+numstr,e);
						}
					}
				}
				meterreads.put(meteraddr+"",new MeterRead(readlogid,gprs.getPid(),meter.getCollectorAddr(),meteraddr+"",meterstatus,meterread,1,remark));
			}
		}

		readService.saveMeterReadsEG(meterreads);

		saveresult.put("good",goodmeters.size());
		saveresult.put("error",errormeters.size());
		return saveresult;
	}

	/**
	 * 保存EG协议的表到DB  抄采集器
	 * @param session
	 * @return
     */
	public HashMap<String,Integer> saveReadDataEGAll(IoSession session){
		HashMap<String,Integer> saveresult = new HashMap<>();
		List<Frame> frames = (List<Frame>)session.getAttribute("frames");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int readlogid = (int)session.getAttribute("readlogid");
		int collector_index = (int)session.getAttribute("collector_index");
		List<Collector> collectors = (List<Collector>)session.getAttribute("collectors");

		Collector collector = collectors.get(collector_index);
		HashMap<String,MeterRead> meterreads = new HashMap<>();
		HashMap<String,String> goodmeters = new HashMap<>();
		HashMap<String,String> errormeters = new HashMap<>();

		for(Frame frame : frames){
			byte[] frame_bytes = frame.getFrame();
			int metercnt = (frame.getDataLength()-12)/3;

			int meterread = -1;
			int meteraddr = 1;
			byte meterstatus = 1;
			String remark = "";

			if(frame_bytes[17] == (byte)0xFF){  //采集器超时~~~~返回指令0xFF
				for(int i = 1;i <= collector.getMeterNums();i++){
					errormeters.put(i+"","");
				}
				break;
			}else{
				for(int i = 0;i < metercnt;i++){
					meteraddr = frame_bytes[18+3*i]&0xFF;
					meterread = frame_bytes[18+3*i+1]&0xFF;
					meterread = meterread << 8;
					meterread = meterread|(frame_bytes[18+3*i+2]&0xFF);
					String numstr = Integer.toHexString(meterread);

					if(numstr.equals("aaaa")){
						meterread = -1;
						meterstatus = 2;
						remark = "aaaa";
						errormeters.put(meteraddr+"","");
					}else{
						if(numstr.equals("bbbb")){
							meterread = -1;
							meterstatus = 3;
							remark = "bbbb";
							errormeters.put(meteraddr+"","");
						}else{
							meterstatus = 1;
							remark = "";
							try {
								meterread = Integer.valueOf(numstr);
								goodmeters.put(meteraddr+"","");
							} catch (NumberFormatException e) {
								errormeters.put(meteraddr+"","");
								meterread = -1;
								remark = e.getMessage();
								logger.error("numstr to meterread error ! meterread: "+numstr,e);
							}
						}
					}

					meterreads.put(meteraddr+"",new MeterRead(readlogid,gprs.getPid(),collector.getColAddr()+"",meteraddr+"",meterstatus,meterread,1,remark));
				}
			}
		}

		readService.saveMeterReadsEG(meterreads);

		saveresult.put("good",goodmeters.size());
		saveresult.put("error",errormeters.size());
		return saveresult;
	}

	/**
	 * 保存188协议的表到DB
	 * @param session
	 * @return
     */
	public HashMap<String,Integer> saveReadData188(IoSession session){
		HashMap<String,Integer> saveresult = new HashMap<>();
		List<Frame> frames = (List<Frame>)session.getAttribute("frames");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		int readlogid = (int)session.getAttribute("readlogid");

		HashMap<String,String> goodmeters = new HashMap<>();
		HashMap<String,String> errormeters = new HashMap<>();
		HashMap<String,MeterRead> meterreads = new HashMap<>();
		ByteBuffer bf = ByteBuffer.allocate(4);
		bf.order(ByteOrder.LITTLE_ENDIAN);

		for (Frame frame : frames){
			byte[] meterdata = frame.getData();
			int metercnt = (meterdata.length-1-3-4)/14;
			for (int i = 0;i < metercnt;i++){
				String meteraddr = "";
				int meterread = -1;
				byte meterstatus = 1;
				byte meterstatus_l = 0;
				byte meterstatus_h = 0;
				byte valvestatus = 1;
				String remark = "";

				meterstatus_l = meterdata[i*14+4+1+3+12];
				meterstatus_h = meterdata[i*14+4+1+3+12+1];
				valvestatus = meterdata[i*14+4+1+3+12];
				remark = meterstatus_l+" " +meterstatus_h;

				//表地址
				for(int j = 0;j<7;j++){
					meteraddr += String.format("%02x", meterdata[14*i +4+ 1+3+6-j]&0xFF);
				}

				//meter status 表的状态
				if(((meterstatus_l &0x40) ==0x40) || ((meterstatus_l &0x80)==0x80)){  //timeout
					//0x40 ~ 表  0x80 ~ 采集器
					meterstatus = 4;
					errormeters.put(meteraddr,"");
				}else{  // normal
					goodmeters.put(meteraddr,"");
					if((meterstatus_h & 0x20) == 0x20){  //remark = "气泡";
						meterstatus = 6;
					}else{
						if((meterstatus_h & 0x30) == 0x30){  //remark = "致命故障";
							meterstatus = 7;
						}else{
							if((meterstatus_h & 0x80) == 0x80){  //remark = "强光";
								meterstatus = 8;
							}else{  //remark = "";
								meterstatus = 1;
							}
						}
					}
				}

				//valve status 阀门状态
				switch (valvestatus & 0x03) {
					case 0x00:  //开
						valvestatus = 1;
						break;
					case 0x01:  //关
						valvestatus = 0;
						break;
					case 0x02:  //关
						valvestatus = 0;
						break;
					case 0x03:  //异常
						valvestatus = 2;
						break;
				}


				//表读数
				bf.put(meterdata, i*14+4+1+3+8, 4);
				String readhexstr = Integer.toHexString(bf.getInt(0));  //get the int   turn the int to hex string
				try {
					meterread = Integer.parseInt(readhexstr)/100;  //turn the readhexstr to the real read
				} catch (NumberFormatException e) {
					meterread = -1;
					logger.error("numstr to meterread error ! meterread: "+readhexstr,e);
				}
				bf.flip();
				meterreads.put(meteraddr,new MeterRead(readlogid,gprs.getPid(),meteraddr,meterstatus,meterread,valvestatus,remark));
			}
		}

		readService.saveMeterReads188(meterreads);

		saveresult.put("good",goodmeters.size());
		saveresult.put("error",errormeters.size());
		return saveresult;
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
		ConcurrentHashMap<String,String> gprs_finish = (ConcurrentHashMap<String, String>) session.getAttribute("gprs_finish");

		if(frame.getFn() == 0x01){
            //抄表指令OK  等待接收数据
            session.setAttribute("state","data");
        }else {
            //抄表指令失败  或者这个帧不是应答帧  集中器返回的指令有问题
			//这个地方不去尝试了  直接失败  如果是超时了才去尝试
//			gprs_finish.put(gprs.getGprsaddr(),"抄表指令应答失败");
//			session.closeNow();
			readdata(session,frame);
			session.setAttribute("state","data");
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
		ConcurrentHashMap<String,String> gprs_finish = (ConcurrentHashMap<String, String>) session.getAttribute("gprs_finish");

		if(frame.getFn() == 0x01){
            //同步序列号OK  发送抄表指令
            session.setAttribute("state","readack");
            session.write(readFrame(session));
        }else {
            //同步序列号失败  或者这个帧不是应答帧
			//这个地方不去尝试了  直接失败  如果是超时了才去尝试
			gprs_finish.put(gprs.getGprsaddr(),"同步序列号失败");
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
		ConcurrentHashMap<String,String> gprs_finish = (ConcurrentHashMap<String, String>) session.getAttribute("gprs_finish");

		if(frame.getFn() == 0x01){
            //online 集中器在线  EGatom&188同步序列号 / 188v2发送抄表指令
            switch (gprs.getGprsprotocol()){
                case GPRS.GPRSPROTOCOL_188:  //188
                case GPRS.GPRSPROTOCOL_EGATOM:  //EGatom
					session.setAttribute("state","synack");
                    session.write(synFrame(gprs,seq));
                    break;
                case GPRS.GPRSPROTOCOL_188V2:  //188v2
					session.setAttribute("state","readack");
					session.write(readFrame(session));
                    break;
            }
        }else {
            //offline 集中器不在线  或者这个帧不是应答帧
			gprs_finish.put(gprs.getGprsaddr(),"集中器不在线");
			//在session 关闭的时候 处理：readService.updateReadLog
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

		//add seq
		seq++;
		seq = (byte) (seq &0x0F);
		session.setAttribute("seq", seq);

		Frame frame = null;
		//抄表
		if(action == "read"){
			switch (gprs.getGprsprotocol()){
				case GPRS.GPRSPROTOCOL_188:  //188
					frame = readFrame188(session);
					break;
				case GPRS.GPRSPROTOCOL_EGATOM:  //EG表
					frame = readFrameEG(session);
					break;
				case GPRS.GPRSPROTOCOL_188V2:  //188 v2
					frame = readFrame188v2(session);
					break;
			}
		}
		//开关阀
		if(action == "valve"){
			switch (gprs.getGprsprotocol()){
				case GPRS.GPRSPROTOCOL_188:  //188
//					frame = controlFrame188(action, gprs, meter, seq);
					break;
			}
		}

		return frame;
	}

	/**
	 * 188协议对应的抄表指令
	 * @param session
	 * @return
     */
	private Frame readFrame188(IoSession session) {
		Meter meter = (Meter)session.getAttribute("meter");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		byte seq = (byte)session.getAttribute("seq");
		String read_type = (String)session.getAttribute("read_type");

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
	 * @param session
	 * @return
     */
	private Frame readFrame188v2(IoSession session) {
		Meter meter = (Meter)session.getAttribute("meter");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		byte seq = (byte)session.getAttribute("seq");
		String read_type = (String)session.getAttribute("read_type");

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
	 * @param session
	 * @return
     */
	private Frame readFrameEG(IoSession session) {
		Meter meter = (Meter)session.getAttribute("meter");
		GPRS gprs = (GPRS)session.getAttribute("gprs");
		byte seq = (byte)session.getAttribute("seq");
		String read_type = (String)session.getAttribute("read_type");

		byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());
		byte[] framedata = new byte[4];

		switch (read_type){
			case "single":
				framedata[0] = (byte) 0xAA;
				framedata[1] = (byte) (Integer.parseInt(meter.getCollectorAddr()) / 256);
				framedata[2] = (byte) Integer.parseInt(meter.getCollectorAddr());
				framedata[3] = (byte) Integer.parseInt(meter.getMeterAddr());//Byte.parseByte(meter.getMeterAddr());
				break;
			case "all":
				//这个需要一个采集器一个采集器的来
				List<Collector> collectors = (List<Collector>)session.getAttribute("collectors");
				int collector_index = (int)session.getAttribute("collector_index");
				Collector collector = collectors.get(collector_index);
				framedata[0] = 0x00;
				framedata[1] = (byte) (collector.getColAddr() / 256);
				framedata[2] = (byte) collector.getColAddr();
				framedata[3] = (byte) collector.getMeterNums();
				//当这个采集器抄表完成之后  collector_index + 1
				break;
		}

		Frame read = new Frame(framedata.length, (byte)(Frame.ZERO | Frame.PRM_MASTER |Frame.PRM_M_SECOND),
				Frame.AFN_READMETER, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR|seq),
				(byte)0x04, gprs_addr, framedata);
		return read;
	}

}
