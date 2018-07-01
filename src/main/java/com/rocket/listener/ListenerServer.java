package com.rocket.listener;

import java.net.InetSocketAddress;

import com.rocket.codec.FrameCodecFactory;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ListenerServer {
	
	private static final Logger logger = LoggerFactory.getLogger(ListenerServer.class);
	public static NioSocketAcceptor acceptor = null;  //TCP Server

	public static void main(String[] args){
		logger.info("start");
		if(args.length != 2){
			System.exit(-1);
			logger.error("args error please use : cmd ip port ");
		}

		String ip = args[0];
		String port = args[1];

		acceptor = new NioSocketAcceptor();
		acceptor.getSessionConfig().setReadBufferSize(2048);
		acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 300);
		acceptor.getFilterChain().addLast("protocol",
				new ProtocolCodecFilter(new FrameCodecFactory()));
		
		acceptor.setHandler(new ListenerDataHandler());
		try {
			acceptor.bind(new InetSocketAddress(ip,Integer.parseInt(port)));
			logger.info("server start at ip: "+ip+" ;port: "+port);
		} catch (Exception e) {
			logger.error("server start error ！",e);
		}

	}

}
