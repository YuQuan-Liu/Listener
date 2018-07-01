package com.rocket.utils;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Rocket on 2018/6/27.
 */
public class MybatisUtils {

    private static SqlSessionFactory sqlSessionFactoryRemote;
    private static SqlSessionFactory sqlSessionFactoryListener;
    static {
        String resource = "mybatis-config.xml";
        try {
            sqlSessionFactoryRemote = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream(resource),"remotemeter");
            sqlSessionFactoryListener = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream(resource),"listener");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SqlSessionFactory getSqlSessionFactoryListener(){
        return sqlSessionFactoryListener;
    }

    public static SqlSessionFactory getSqlSessionFactoryRemote(){
        return sqlSessionFactoryRemote;
    }

}
