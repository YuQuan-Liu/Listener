package com.rocket.listener.obj;

import java.util.Date;

public class ListenerLog {
	
	private int pid;
	private String GPRSTel;
	private String src;
	private String type;
	private String remoteAddr;
	private String data;
	private String transfered;
	private Date date;
	public int getPid() {
		return pid;
	}
	public void setPid(int pid) {
		this.pid = pid;
	}
	public String getGPRSTel() {
		return GPRSTel;
	}
	public void setGPRSTel(String gPRSTel) {
		GPRSTel = gPRSTel;
	}
	public String getSrc() {
		return src;
	}
	public void setSrc(String src) {
		this.src = src;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getRemoteAddr() {
		return remoteAddr;
	}
	public void setRemoteAddr(String remoteAddr) {
		this.remoteAddr = remoteAddr;
	}
	public String getData() {
		return data;
	}
	public void setData(String data) {
		this.data = data;
	}
	public String getTransfered() {
		return transfered;
	}
	public void setTransfered(String transfered) {
		this.transfered = transfered;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	public ListenerLog(String gPRSTel, String src, String type, String data,String remoteAddr) {
		super();
		GPRSTel = gPRSTel;
		this.src = src;
		this.type = type;
		this.data = data;
		this.remoteAddr = remoteAddr;
	}
	public ListenerLog() {
		super();
	}
	
}
