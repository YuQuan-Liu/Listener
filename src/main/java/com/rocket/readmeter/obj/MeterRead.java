package com.rocket.readmeter.obj;

/**
 * 抄表结果  存库实例类
 * Created by Rocket on 2018/7/3.
 */
public class MeterRead {

    private String meteraddr;
    private int readlogid;
    private int gprsid;
    private int meterstatus;
    private int meterread;
    private int valvestatus;
    private String remark;

    public MeterRead(int readlogid, int gprsid, String meteraddr, int meterstatus, int meterread, int valvestatus, String remark) {
        this.meteraddr = meteraddr;
        this.readlogid = readlogid;
        this.gprsid = gprsid;
        this.meterstatus = meterstatus;
        this.meterread = meterread;
        this.valvestatus = valvestatus;
        this.remark = remark;
    }

    public String getMeteraddr() {
        return meteraddr;
    }

    public void setMeteraddr(String meteraddr) {
        this.meteraddr = meteraddr;
    }

    public int getReadlogid() {
        return readlogid;
    }

    public void setReadlogid(int readlogid) {
        this.readlogid = readlogid;
    }

    public int getGprsid() {
        return gprsid;
    }

    public void setGprsid(int gprsid) {
        this.gprsid = gprsid;
    }

    public int getMeterstatus() {
        return meterstatus;
    }

    public void setMeterstatus(int meterstatus) {
        this.meterstatus = meterstatus;
    }

    public int getMeterread() {
        return meterread;
    }

    public void setMeterread(int meterread) {
        this.meterread = meterread;
    }

    public int getValvestatus() {
        return valvestatus;
    }

    public void setValvestatus(int valvestatus) {
        this.valvestatus = valvestatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
