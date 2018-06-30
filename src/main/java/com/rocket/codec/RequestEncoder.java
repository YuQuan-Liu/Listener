package com.rocket.codec;

import com.rocket.readmeter.obj.Frame;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

public class RequestEncoder extends ProtocolEncoderAdapter {

	@Override
	public void encode(IoSession session, Object message,
			ProtocolEncoderOutput out) throws Exception {
		Frame frame = (Frame) message;
		IoBuffer buffer = IoBuffer.allocate(frame.getFrame().length).put(frame.getFrame()).flip();
		out.write(buffer);
	}

}
