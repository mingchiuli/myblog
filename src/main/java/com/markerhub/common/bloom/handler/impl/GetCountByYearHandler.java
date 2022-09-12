package com.markerhub.common.bloom.handler.impl;

import com.markerhub.common.bloom.handler.BloomHandler;
import com.markerhub.common.exception.NoFoundException;
import com.markerhub.common.lang.Const;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class GetCountByYearHandler implements BloomHandler {

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String methodName() {
        return "getCountByYear";
    }

    @Override
    public void handler(Object[] args) {
        Integer year = (Integer) args[0];
        if (Boolean.FALSE.equals(redisTemplate.opsForValue().getBit(Const.BLOOM_FILTER_YEARS, year))) {
            throw new NoFoundException("没有" + year + "年份！");
        }
    }
}
