package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.Valvelog;
import org.apache.ibatis.annotations.Select;

/**
 * Created by Rocket on 2018/6/27.
 */
public interface ValveLogMapper {

    @Select("select pid,adminid,actionTime,auto,actionCount,completeCount,errorCount,status,failReason,remark from ValveLog " +
            "where pid = #{pid}")
    public Valvelog getByID(int pid);

    @Select("update valvelog " +
            "set completecount = #{normal},errorcount=#{error},status = 100 " +
            "where pid = #{pid}")
    public void updateValveLog(Valvelog valvelog, int normal, int error);
    
}