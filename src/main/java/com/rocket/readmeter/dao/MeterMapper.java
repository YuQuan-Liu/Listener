package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.Collector;
import com.rocket.readmeter.obj.GPRS;
import com.rocket.readmeter.obj.Meter;
import com.rocket.readmeter.obj.MeterRead;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public interface MeterMapper {

    @Select("select CollectorAddr colAddr,COUNT(*) meterNums from meter " +
            "where gprsid = #{gid} and Valid = 1 " +
            "group by CollectorAddr " +
            "order by CollectorAddr")
    public List<Collector> getCollectorsByGID(int gid);

    @Select("select collectorAddr,meterAddr from meter " +
            "where pid = #{mid}")
    public Meter getMeterbyID(int mid);

    @Select("select count(*) from meter " +
            "where gprsid = #{gid} and valid = 1")
    public int getMeterCountByGID(int gid);

    @Select("update Meter " +
            "set meterstate = #{meterstatus},readdata = #{meterread},valvestate = #{valvestatus},readtime = now() " +
            "where gprsid = #{gprsid} and MeterAddr = #{meteraddr} and valid = '1' ")
    public void updateMeter(MeterRead meterRead);

    @Select("update Meter " +
            "set meterstate = #{meterstatus},valvestate = #{valvestatus},readtime = now() " +
            "where gprsid = #{gprsid} and MeterAddr = #{meteraddr} and valid = '1' ")
    public void updateMeterNoRead(MeterRead meterRead);

}
