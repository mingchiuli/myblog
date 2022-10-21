package com.markerhub.common.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.service.BlogService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * 统一缓存处理
 * @author mingchiuli
 * order: 多个切面执行顺序，越小越先执行
 * @create 2021-12-01 7:48 AM
 */
@Aspect
@Component
@Slf4j
@Order(2)
public class CacheAspect {

    private static final String LOCK = "lock:";

    BlogService blogService;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private void setRedisTemplateImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Pointcut("@annotation(com.markerhub.common.cache.Cache)")
    public void pt() {}

    @SneakyThrows
    @Around("pt()")
    public Object around(ProceedingJoinPoint pjp) {
        Signature signature = pjp.getSignature();
        //类名
        String className = pjp.getTarget().getClass().getSimpleName();
        //调用的方法名
        String methodName = signature.getName();

        Class<?>[] parameterTypes = new Class[pjp.getArgs().length];
        Object[] args = pjp.getArgs();
        //参数
        StringBuilder params = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                //方法的参数必须是能够json化的
                params.append("::");
                if (args[i] instanceof String) {
                    params.append(args[i]);
                } else {
                    params.append(objectMapper.writeValueAsString(args[i]));
                }
                parameterTypes[i] = args[i].getClass();
            } else {
                parameterTypes[i] = null;
            }
        }

        Method method = pjp.getSignature().getDeclaringType().getMethod(methodName, parameterTypes);

        Cache annotation = method.getAnnotation(Cache.class);
        long expire = annotation.expire();
        String name = annotation.name();

        Type genericReturnType = method.getGenericReturnType();

        JavaType javaType;

        if (genericReturnType instanceof ParameterizedType parameterizedType) {
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

            Class<?>[] classes = new Class<?>[actualTypeArguments.length];
            for (int i = 0; i < classes.length; i++) {
                classes[i] = (Class<?>) actualTypeArguments[i];
            }

            javaType = objectMapper.getTypeFactory().constructParametricType(rawType, classes);
        } else {
            javaType = objectMapper.getTypeFactory().constructType(genericReturnType);
        }

        String redisKey = StringUtils.hasLength(name) ? name + "::" + className + "::" + methodName + params : className + "::" + methodName + params;

        Object o;

        //防止redis挂了以后db也访问不了
        try {
            o = redisTemplate.opsForValue().get(redisKey);
        } catch (NestedRuntimeException e) {
            return pjp.proceed();
        }

        if (o != null) {
            return objectMapper.convertValue(o, javaType);
        }

        String lock = (LOCK + className + methodName + params).intern();

        //防止缓存击穿
        synchronized (lock) {
            //双重检查
            Object r = redisTemplate.opsForValue().get(redisKey);

            if (r != null) {
                return objectMapper.convertValue(r, javaType);
            }
            //执行目标方法
            Object proceed = pjp.proceed();
            redisTemplate.opsForValue().set(redisKey, proceed, expire, TimeUnit.SECONDS);
            return proceed;
        }

    }
}
