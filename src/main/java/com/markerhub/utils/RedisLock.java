package com.markerhub.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author mingchiuli
 * @create 2023-02-02 10:48 pm
 */
@Component
public class RedisLock {

    RedisTemplate<String, Object> redisTemplate;

    public RedisLock(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String key, String val, long timeOut, long expireTime){
        long start = System.currentTimeMillis();
        for (;;) {
            Boolean b = redisTemplate.opsForValue().setIfAbsent(key, val, expireTime, TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(b)){
                return true;
            }
            if (System.currentTimeMillis() - start > timeOut){
                return false;
            }
        }
    }

    public void unLock(String key, String val){
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        redisTemplate.execute(redisScript, Collections.singletonList(key), val);
    }
}
