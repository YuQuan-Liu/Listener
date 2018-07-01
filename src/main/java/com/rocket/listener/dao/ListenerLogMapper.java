package com.rocket.listener.dao;

import com.rocket.listener.obj.ListenerLog;
import org.apache.ibatis.annotations.Select;

/**
 * Created by Rocket on 2018/7/1.
 */
public interface ListenerLogMapper {

    @Select("insert into ListenerLog " +
            "(GPRSTel,Src,Type,Data,RemoteAddr) " +
            "values(#{GPRSTel},#{src},#{type},#{data},#{remoteAddr}) ")
    public void insertLog(ListenerLog log);

}
