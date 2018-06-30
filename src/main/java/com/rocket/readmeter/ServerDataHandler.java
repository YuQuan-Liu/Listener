package com.rocket.readmeter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rocket.readmeter.service.ReadService;
import com.rocket.readmeter.service.ValveService;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerDataHandler extends IoHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(ServerDataHandler.class);
	private static final Gson gson = new Gson();
	private final ReadService readService = new ReadService();
	private final ValveService valveService = new ValveService();

	@Override
	public void exceptionCaught(IoSession session,Throwable cause){
		session.closeNow();
		logger.error("MINA error", cause);
	}
	
	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
		
		if(session.getIdleCount(status) == 1){
			session.closeNow();
		}
		
	}
	
	@Override
	public void messageReceived(IoSession session,Object message){
		String action = (String) message;
		logger.info(session.getRemoteAddress().toString()+action);
		try {
			JsonObject jo = gson.fromJson(action,JsonObject.class);
			String function = jo.getAsJsonPrimitive("function").getAsString();
			int readlogid = jo.getAsJsonPrimitive("pid").getAsInt();
			if((function.equalsIgnoreCase("read") || function.equalsIgnoreCase("valve")) && readlogid > 0){
				session.write("{\"function\":\""+function+"\",\"pid\":\""+readlogid+"\",\"result\":\"success\"}");
				switch (function){
					case "read":
						readService.read(readlogid);
						break;
					case "valve":
						break;
				}
			}else{
				session.write("{\"function\":\""+function+"\",\"pid\":\""+readlogid+"\",\"result\":\"fail\"}");
			}
		} catch (Exception e) {
			logger.error("message receive error : " + action,e);
		}
	}
	
	@Override
	public void sessionOpened(IoSession session) throws Exception{
		logger.info(session.getRemoteAddress().toString());
	}
	
	@Override
	public void sessionClosed(IoSession session) throws Exception{
		
	}
}
