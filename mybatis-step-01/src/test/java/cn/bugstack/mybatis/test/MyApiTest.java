package cn.bugstack.mybatis.test;

import cn.bugstack.mybatis.test.dao.IUserDao;
import com.alibaba.fastjson.JSON;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;

public class MyApiTest {

    Logger log = LoggerFactory.getLogger(MyApiTest.class);

    @Test
    public void testMyUserDao() {
        IUserDao iUserDao = (IUserDao) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{IUserDao.class},
                (proxy, method, args) -> "你被代理了！");

        String res = iUserDao.queryUserName("1");
        log.info("测试结果: {}", res);
    }
}
