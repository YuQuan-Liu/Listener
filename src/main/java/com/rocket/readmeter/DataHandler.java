package com.rocket.readmeter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DataHandler extends IoHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(DataHandler.class);
	private static final Gson gson = new Gson();

	@Override
	public void exceptionCaught(IoSession session,Throwable cause){
		session.close(true);
		logger.error("MINA错误", cause);
	}
	
	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
		
		if(session.getIdleCount(status) == 1){
			session.close(true);
		}
		
	}
	
	@Override
	public void messageReceived(IoSession session,Object message){
		String action = (String) message;
		logger.info(session.getRemoteAddress().toString()+action);
		try {
			JsonObject jo = gson.fromJson(action,JsonObject.class);
			String function = jo.getAsJsonPrimitive("function").getAsString();
			int pid = jo.getAsJsonPrimitive("pid").getAsInt();
			if((function.equalsIgnoreCase("read") || function.equalsIgnoreCase("valve")) && pid > 0){
				session.write("{\"function\":\""+function+"\",\"pid\":\""+pid+"\",\"result\":\"success\"}");
				//将指令添加到处理的线程池
//				DealAction.addAction(function, pid);
			}else{
				session.write("{\"function\":\""+function+"\",\"pid\":\""+pid+"\",\"result\":\"fail\"}");
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
