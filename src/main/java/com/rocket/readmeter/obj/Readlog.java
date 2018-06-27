package com.rocket.readmeter.obj;

public class Readlog {
	private int pid;
	private int adminid;
	private int objectId;
	private int readType;
	private int remote;
	private int readObject;
	private String ip;
	private int readStatus;
	private String failReason;
	private String startTime;
	private String completeTime;
	private int settle;
	private String result;

	public int getPid() {
		return pid;
	}

	public void setPid(int pid) {
		this.pid = pid;
	}

	public int getAdminid() {
		return adminid;
	}

	public void setAdminid(int adminid) {
		this.adminid = adminid;
	}

	public int getObjectId() {
		return objectId;
	}

	public void setObjectId(int objectId) {
		this.objectId = objectId;
	}

	public int getReadType() {
		return readType;
	}

	public void setReadType(int readType) {
		this.readType = readType;
	}

	public int getRemote() {
		return remote;
	}

	public void setRemote(int remote) {
		this.remote = remote;
	}

	public int getReadObject() {
		return readObject;
	}

	public void setReadObject(int readObject) {
		this.readObject = readObject;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getReadStatus() {
		return readStatus;
	}

	public void setReadStatus(int readStatus) {
		this.readStatus = readStatus;
	}

	public String getFailReason() {
		return failReason;
	}

	public void setFailReason(String failReason) {
		this.failReason = failReason;
	}
	
	public String getStartTime() {
		return startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	public String getCompleteTime() {
		return completeTime;
	}

	public void setCompleteTime(String completeTime) {
		this.completeTime = completeTime;
	}

	public int getSettle() {
		return settle;
	}

	public void setSettle(int settle) {
		this.settle = settle;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public Readlog() {
	}

	@Override
	public String toString() {
		return "Readlog [pid=" + pid + ", adminid=" + adminid + ", objectId="
				+ objectId + ", readType=" + readType + ", remote=" + remote
				+ ", readObject=" + readObject + ", ip=" + ip + ", readStatus="
				+ readStatus + ", failReason=" + failReason + ", startTime="
				+ startTime + ", completeTime=" + completeTime + ", settle="
				+ settle + ", result=" + result + "]";
	}

}
