package com.markerhub.common.bloom;

import com.markerhub.common.bloom.handler.BloomHandler;
import com.markerhub.common.exception.NoFoundException;
import com.markerhub.common.lang.Const;
import com.markerhub.service.BlogService;
import com.markerhub.utils.MyUtils;
import com.markerhub.utils.SpringUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author mingchiuli
 * @create 2022-06-07 11:01 AM
 */
@Aspect
@Component
@Slf4j
@Order(1)
public class BloomAspect {

    volatile Map<String, BloomHandler> cacheHandlers;

    BlogService blogService;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Pointcut("@annotation(com.markerhub.common.bloom.Bloom)")
    public void pt() {}

    @SneakyThrows
    @Before("pt()")
    public void before(JoinPoint jp) {

        Signature signature = jp.getSignature();
        //方法名
        String methodName = signature.getName();
        //参数
        Object[] args = jp.getArgs();

        if (cacheHandlers == null) {
            synchronized (this) {
                if (cacheHandlers == null) {
                    cacheHandlers = SpringUtils.getHandlers(BloomHandler.class);
                }
            }
        }

        for (BloomHandler handler : cacheHandlers.values()) {
            if (methodName.equals(handler.methodName())) {
                handler.doHand(args);
                break;
            }
        }
    }
}
