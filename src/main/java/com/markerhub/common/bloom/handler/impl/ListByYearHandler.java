package com.markerhub.common.bloom.handler.impl;

import com.markerhub.common.bloom.BloomEnum;
import com.markerhub.common.bloom.handler.BloomHandler;
import com.markerhub.common.exception.NoFoundException;
import com.markerhub.common.lang.Const;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ListByYearHandler implements BloomHandler {
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public BloomEnum mark() {
        return BloomEnum.LIST_BY_YEAR;
    }

    @Override
    public void doHand(Object[] args) {
        Integer currentPage = (Integer) args[0];
        Integer yearMark = (Integer) args[1];
        if (Boolean.FALSE.equals(redisTemplate.opsForValue().getBit(Const.BLOOM_FILTER_PAGE + yearMark, currentPage))) {
            throw new NoFoundException("没有" + yearMark + "年份" + currentPage + "页面！");
        }
    }
}
