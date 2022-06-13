package com;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mingchiuli
 * @create 2021-11-14 7:06 PM
 */
public class Test {




    //    @org.junit.Test
    public void test() {
        String str = "我+A";
        System.out.println(str.length());
        for (int i = 0; i < str.length(); i++) {
            System.out.println(str.charAt(i));
        }
    }

//    @org.junit.Test
    public void test2() {
        ConcurrentHashMap<Object, Object> objectObjectConcurrentHashMap = new ConcurrentHashMap<>();
        objectObjectConcurrentHashMap.put("111", "111");
        objectObjectConcurrentHashMap.put("111", "222");
        System.out.println(objectObjectConcurrentHashMap);
    }

    @org.junit.jupiter.api.Test
    public void test3() {
        // 加密后密码

        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        String password = bCryptPasswordEncoder.encode("111111");

        System.out.println(password);

        boolean matches = bCryptPasswordEncoder.matches("111111", password);

        System.out.println("匹配结果：" + matches);
    }
}
