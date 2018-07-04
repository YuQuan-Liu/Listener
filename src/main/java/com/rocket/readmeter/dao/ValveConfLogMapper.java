package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.ValveConfLog;
import com.rocket.readmeter.obj.Valvelog;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public interface ValveConfLogMapper {

    @Select("select vcl.PID pid,vcl.MeterID meterid,vcl.Switch switchaction,vcl.Result result,vcl.ErrorReason errorreason,vcl.ValveLogID valvelogid, " +
            "m.MeterAddr meteraddr,m.GPRSID gprsid, " +
            "g.IP ip,g.Port port,g.GPRSProtocol gprsprotocol,g.GPRSAddr gprsaddr from ValveConfLog vcl " +
            "join Meter m " +
            "on vcl.MeterID = m.PID " +
            "join GPRS g " +
            "on m.GPRSID = g.PID " +
            "where vcl.ValveLogID = #{valvelogid} ")
    public List<ValveConfLog> getValveConfLog(int valvelogid);

    @Update("update valveconflog " +
            "set result = #{result},errorReason =#{errorreason},errorstatus = 0,completetime = now() " +
            "where pid = #{pid}")
    public void updateValveConfLog(ValveConfLog valveConfLog);

}
