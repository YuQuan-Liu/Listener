import com.rocket.listener.dao.ListenerLogMapper;
import com.rocket.listener.obj.ListenerLog;
import com.rocket.readmeter.ClientDataHandler;
import com.rocket.readmeter.dao.GPRSMapper;
import com.rocket.readmeter.dao.MeterMapper;
import com.rocket.readmeter.dao.ReadLogMapper;
import com.rocket.readmeter.obj.Frame;
import com.rocket.readmeter.obj.GPRS;
import com.rocket.readmeter.obj.MeterRead;
import com.rocket.readmeter.service.ReadService;
import com.rocket.utils.MybatisUtils;
import com.rocket.utils.StringUtil;
import org.apache.ibatis.session.SqlSession;

import java.util.HashMap;
import java.util.List;

/**
 * Created by Rocket on 2018/6/27.
 */
public class Test {


    @org.junit.Test
    public void testGPRSMapper(){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        GPRSMapper gprsMapper = session.getMapper(GPRSMapper.class);
        List<GPRS> gprss = gprsMapper.getGPRSsbyNID(136);
        System.out.println(gprss.size());

        session.commit();
        session.close();
    }

    @org.junit.Test
    public void testMeterMapper(){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        MeterMapper meterMapper = session.getMapper(MeterMapper.class);
        int metercnt = meterMapper.getMeterCountByGID(11);
        System.out.println(metercnt);

        session.commit();
        session.close();
    }

    @org.junit.Test
    public void testListenerLogMapper(){
        SqlSession session = MybatisUtils.getSqlSessionFactoryListener().openSession();

        ListenerLogMapper listenerLogMapper = session.getMapper(ListenerLogMapper.class);
        listenerLogMapper.insertLog(new ListenerLog("5555", "1", "3", "GPRS","remote"));

        session.commit();
        session.close();
    }

    @org.junit.Test
    public void testReadLogMapper(){
        SqlSession session = MybatisUtils.getSqlSessionFactoryRemote().openSession();

        ReadLogMapper readLogMapper = session.getMapper(ReadLogMapper.class);
        readLogMapper.updateReadLog(17737, "good","good");
        session.commit();
        session.close();
    }

    @org.junit.Test
    public void testFrame(){
        GPRS gprs = new GPRS();
        gprs.setGprsaddr("5700000422");
        Frame syn = new ClientDataHandler().synFrame(gprs,1);
        System.out.println(syn);

        //GPRS 心跳帧
        byte[] gprs_addr = StringUtil.string2Byte(gprs.getGprsaddr());
        Frame login = new Frame(0, (byte)0xC9,
                Frame.AFN_LOGIN, (byte)(Frame.ZERO|Frame.SEQ_FIN|Frame.SEQ_FIR),
                (byte)0x03, gprs_addr, new byte[0]);
        System.out.println(login);
    }

    @org.junit.Test
    public void testReadService(){
        ReadService readService = new ReadService();
        HashMap<String,MeterRead> meterreads = new HashMap<>();

        meterreads.put("00001510161009",new MeterRead(17828,661,"00001510161009",1,123,2,"Test"));
        meterreads.put("00001605079446",new MeterRead(17828,661,"00001605079446",1,9999,2,"Test"));

        readService.saveMeterReads(meterreads);

    }
}
