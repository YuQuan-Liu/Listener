package com.rocket.listener.service;

import com.rocket.listener.dao.ListenerLogMapper;
import com.rocket.listener.obj.ListenerLog;
import com.rocket.utils.MybatisUtils;
import org.apache.ibatis.session.SqlSession;

/**
 * Created by Rocket on 2018/7/1.
 */
public class ListenerLogService {

    public void insertListenerLog(ListenerLog listenerLog){
//        SqlSession session = MybatisUtils.getSqlSessionFactoryListener().openSession();
//        try {
//            ListenerLogMapper listenerLogMapper = session.getMapper(ListenerLogMapper.class);
//            listenerLogMapper.insertLog(listenerLog);
//            session.commit();
//        } finally {
//            session.close();
//        }
    }
    
}
