package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.Readlog;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public interface ReadLogMapper {

    @Select("select pid,adminid,objectId,readType,remote,readObject,ip,readStatus,failReason,startTime,completeTime,settle,result from ReadLog " +
            "where pid = #{pid} ")
    public Readlog getByID(int pid);

    @Select("select pid,adminid,objectId,readType,remote,readObject,ip,readStatus,failReason,startTime,completeTime,settle,result from ReadLog " +
            "where pid >= #{pid} and adminid = #{adminid} ")
    public List<Readlog> getAllNeighborReadlog(int adminid, int pid);

    @Update("update readlog " +
            "set ReadStatus = 100,FailReason = #{reason},CompleteTime = now(),Result = #{result} " +
            "where PID = #{readlogid}")
    public void updateReadLog(int readlogid, boolean finished,
                                     String reason, String result);

}
