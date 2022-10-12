package com.markerhub.common.bloom.handler.impl;

import com.markerhub.common.bloom.BloomEnum;
import com.markerhub.common.bloom.handler.BloomHandler;
import com.markerhub.common.exception.NoFoundException;
import com.markerhub.common.lang.Const;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class GetBlogStatusHandler implements BloomHandler {

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public BloomEnum mark() {
        return BloomEnum.GET_BLOG_STATUS;
    }

    @Override
    public void doHand(Object[] args) {
        Long blogId = (Long) args[0];
        if (Boolean.FALSE.equals(redisTemplate.opsForValue().getBit(Const.BLOOM_FILTER_BLOG, blogId))) {
            throw new NoFoundException("没有"+ blogId + "号博客！");
        }
    }
}
