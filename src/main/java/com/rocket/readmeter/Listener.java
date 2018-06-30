package com.rocket.readmeter;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import com.rocket.codec.FrameCodecFactory;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Listener {

	private static final Logger logger = LoggerFactory.getLogger(Listener.class);
    public static NioSocketAcceptor acceptor = null;  //TCP Server
    public static NioSocketConnector connector = null;  //TCP Client

    public static void main(String[] args) {
		logger.info("start");

		String ip = args[0];
		String port = args[1];

        //TCP Server
        acceptor = new NioSocketAcceptor();
		acceptor.getSessionConfig().setReadBufferSize(2048);
		acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 10);  //10s
		acceptor.getFilterChain().addLast("protocol",
                new ProtocolCodecFilter(new TextLineCodecFactory(Charset.forName("utf-8"))));
		acceptor.setHandler(new ServerDataHandler());
		try {
			acceptor.bind(new InetSocketAddress(ip,Integer.parseInt(port)));
            logger.info("server start at ip: "+ip+" ;port: "+port);
		} catch (Exception e) {
			logger.error("server start error ！",e);
		}

        //TCP Client
        connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(10*1000);  //10s
        connector.getSessionConfig().setIdleTime(IdleStatus.READER_IDLE,10);  //10s readidle
        connector.getFilterChain().addLast("protocol",
                new ProtocolCodecFilter(new FrameCodecFactory()));

        connector.setHandler(new ClientDataHandler());

	}
}
