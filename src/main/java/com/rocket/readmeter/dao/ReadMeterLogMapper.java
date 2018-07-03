package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.MeterRead;
import com.rocket.readmeter.obj.Readlog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public interface ReadMeterLogMapper {

    @Insert("insert into ReadMeterLog " +
            "(MeterId,ActionType,ActionResult,ReadLogid,remark) " +
            "select pid,#{meterstatus},#{meterread},#{readlogid},#{remark} from Meter " +
            "where gprsid = #{gprsid} and MeterAddr = #{meteraddr} and valid = '1' ")
    public int insertReadMeterLog(MeterRead meterRead);


}
