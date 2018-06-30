package com.rocket.readmeter.dao;

import com.rocket.readmeter.obj.ValveConfLog;
import com.rocket.readmeter.obj.Valvelog;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public interface ValveConfLogMapper {

    public List<ValveConfLog> getValveConfLog(int valvelogid);

    public void updateValveConfLog(ValveConfLog valveConfLog, boolean finished,
                                          String reason);

}
