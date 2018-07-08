package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.GPRS;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public interface GPRSMapper {

    @Select("select pid,neighborid,gprsaddr,gprsprotocol,ip,port from gprs " +
            "where neighborid = #{nid} and valid = 1 ")
    public List<GPRS> getGPRSsbyNID(int nid);

    @Select("select pid,neighborid,gprsaddr,gprsprotocol,ip,port from gprs " +
            "where PID = (select gprsid from Meter where PID = #{mid})")
    public GPRS getGPRSbyMID(int mid);

    @Select("select pid,neighborid,gprsaddr,gprsprotocol,ip,port from gprs " +
            "where PID = #{gid} ")
    public GPRS getGPRSbyID(int gid);

}
