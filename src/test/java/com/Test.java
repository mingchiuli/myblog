package com;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mingchiuli
 * @create 2021-11-14 7:06 PM
 */
public class Test {
    @org.junit.Test
    public void test() {
        String str = "我+A";
        System.out.println(str.length());
        for (int i = 0; i < str.length(); i++) {
            System.out.println(str.charAt(i));
        }
    }

    @org.junit.Test
    public void test2() {
        ConcurrentHashMap<Object, Object> objectObjectConcurrentHashMap = new ConcurrentHashMap<>();
        objectObjectConcurrentHashMap.put("111", "111");
        objectObjectConcurrentHashMap.put("111", "222");
        System.out.println(objectObjectConcurrentHashMap);
    }
}
