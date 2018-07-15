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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ServerDataHandler extends IoHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(ServerDataHandler.class);
	private static final Gson gson = new Gson();
	private final ReadService readService = new ReadService();
	private final ValveService valveService = new ValveService();
	private final static ExecutorService threadpool = Executors.newFixedThreadPool(7);

	@Override
	public void exceptionCaught(IoSession session,Throwable cause){
		session.closeNow();
		logger.error("MINA error", cause);
	}
	
	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
		
		if(session.getIdleCount(status) == 1){
			String remote = session.getRemoteAddress().toString();
			String action = (String) session.getAttribute("action");
			logger.info("action: "+ action+";remote: "+remote+";idle!!!");
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
			int pid = jo.getAsJsonPrimitive("pid").getAsInt();  //readlogid / valvelogid
			if((function.equalsIgnoreCase("read") || function.equalsIgnoreCase("valve")) && pid > 0){
				session.write("{\"function\":\""+function+"\",\"pid\":\""+pid+"\",\"result\":\"success\"}");
				session.setAttribute("action",action);
				//在线程中执行抄表任务
				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						logger.info("thread pool start running action: "+action);
						switch (function){
							case "read":
								readService.read(pid);
								break;
							case "valve":
								valveService.control(pid);
								break;
						}
						logger.info("thread pool end running action: "+action);
					}
				});
				logger.info("threadname: "+thread.getName()+";threadid: "+thread.getId());
				thread.start();

			}else{
				session.write("{\"function\":\""+function+"\",\"pid\":\""+pid+"\",\"result\":\"fail\"}");
			}
		} catch (Exception e) {
			logger.error("message receive error : " + action,e);
		}
	}
	
	@Override
	public void sessionOpened(IoSession session) throws Exception{
		session.setAttribute("action","");  //防止获取action的时候null异常
		logger.info(session.getRemoteAddress().toString());
	}
	
	@Override
	public void sessionClosed(IoSession session) throws Exception{
		String remote = session.getRemoteAddress().toString();
		String action = (String) session.getAttribute("action");
		logger.info("action: "+ action+";remote: "+remote+";closed!!!");
	}
}
