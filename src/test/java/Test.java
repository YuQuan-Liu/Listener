import com.rocket.readmeter.dao.GPRSMapper;
import com.rocket.readmeter.dao.MeterMapper;
import com.rocket.readmeter.obj.GPRS;
import com.rocket.utils.MybatisUtils;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public class Test {


    @org.junit.Test
    public void testGPRSMapper(){
        SqlSession session = MybatisUtils.getSqlSessionFactory().openSession();

        GPRSMapper gprsMapper = session.getMapper(GPRSMapper.class);
        List<GPRS> gprss = gprsMapper.getGPRSsbyNID(136);
        System.out.println(gprss.size());

        session.commit();
        session.close();
    }

    @org.junit.Test
    public void testMeterMapper(){
        SqlSession session = MybatisUtils.getSqlSessionFactory().openSession();

        MeterMapper meterMapper = session.getMapper(MeterMapper.class);
        int metercnt = meterMapper.getMeterCountByGID(11);
        System.out.println(metercnt);

        session.commit();
        session.close();
    }
}
