package com.rocket.readmeter;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Listener {

	private static final Logger logger = LoggerFactory.getLogger(Listener.class);
	public static void main(String[] args) {
		logger.info("start");

		String ip = args[0];
		String port = args[1];

		IoAcceptor acceptor = new NioSocketAcceptor();

		acceptor.getSessionConfig().setReadBufferSize(2048);
		acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 10);  //10s
		
		acceptor.getFilterChain().addLast("protocol", new ProtocolCodecFilter(new TextLineCodecFactory(Charset.forName("utf-8"))));
		
		acceptor.setHandler(new DataHandler());
		try {

			logger.info("ip: "+ip+" ;port: "+port);
			acceptor.bind(new InetSocketAddress(ip,Integer.parseInt(port)));
		} catch (Exception e) {
			logger.error("监听启动时异常！",e);
		}
	}
}
