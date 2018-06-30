package com.rocket.readmeter.service;

import com.rocket.readmeter.dao.ValveLogMapper;
import com.rocket.readmeter.obj.Valvelog;
import com.rocket.utils.MybatisUtils;
import org.apache.ibatis.session.SqlSession;

/**
 * Created by Rocket on 2018/6/28.
 */
public class ValveService {

    public Valvelog getValveLogByID(int pid){
        SqlSession session = MybatisUtils.getSqlSessionFactory().openSession();
        Valvelog valvelog = null;
        try{
            ValveLogMapper valveLogMapper = session.getMapper(ValveLogMapper.class);
            valvelog = valveLogMapper.getByID(pid);
        }finally {
            session.close();
        }

        return valvelog;
    }

}
