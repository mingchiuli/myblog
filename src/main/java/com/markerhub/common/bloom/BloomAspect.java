package com.markerhub.common.bloom;

import com.markerhub.common.bloom.handler.BloomHandler;
import com.markerhub.service.BlogService;
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
import java.lang.reflect.Method;
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

    private static class CacheHandlers {
        private static final Map<String, BloomHandler> cacheHandlers = SpringUtils.getHandlers(BloomHandler.class);
    }

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
        Class<?>[] classes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            classes[i] = args[i].getClass();
        }
        Method method = jp.getSignature().getDeclaringType().getMethod(methodName, classes);

        Bloom bloom = method.getAnnotation(Bloom.class);
        Class<?> aClass = bloom.name();

        for (BloomHandler handler : CacheHandlers.cacheHandlers.values()) {
            if (handler.supports(aClass)) {
                try {
                    handler.handle(args);
                    break;
                } catch (RuntimeException e) {
                    log.info(e.toString());
                }
            }
        }
    }
}
