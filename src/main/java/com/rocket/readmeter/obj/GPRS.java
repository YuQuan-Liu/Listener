package com.rocket.readmeter.obj;

public class GPRS {

	//集中器使用协议
	public static final int GPRSPROTOCOL_EG = 1;
	public static final int GPRSPROTOCOL_188 = 2;
	public static final int GPRSPROTOCOL_EGATOM = 3;
	public static final int GPRSPROTOCOL_D10 = 4;
	public static final int GPRSPROTOCOL_188V2 = 5;

	private Integer pid;
	private int neighborid;
	private String gprsaddr;
	private int gprsprotocol;
	private String ip;
	private int port;
	
	public Integer getPid() {
		return pid;
	}

	public void setPid(Integer pid) {
		this.pid = pid;
	}

	public int getNeighborid() {
		return neighborid;
	}

	public void setNeighborid(int neighborid) {
		this.neighborid = neighborid;
	}

	public String getGprsaddr() {
		return gprsaddr;
	}

	public void setGprsaddr(String gprsaddr) {
		this.gprsaddr = gprsaddr;
	}

	public int getGprsprotocol() {
		return gprsprotocol;
	}

	public void setGprsprotocol(int gprsprotocol) {
		this.gprsprotocol = gprsprotocol;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public GPRS() {
	}


}
