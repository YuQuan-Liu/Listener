package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.Readlog;
import org.apache.ibatis.annotations.Param;
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
    public List<Readlog> getAllNeighborReadlog(@Param("adminid") int adminid,
                                               @Param("pid") int pid);

    @Update("update readlog " +
            "set ReadStatus = 100,FailReason = #{failreason},CompleteTime = now(),Result = #{result} " +
            "where PID = #{readlogid}")
    public int updateReadLog(@Param("readlogid") int readlogid,
                             @Param("failreason") String failreason,
                             @Param("result") String result);

}
