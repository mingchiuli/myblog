package com.markerhub.common.bloom;

import com.markerhub.common.lang.Const;
import com.markerhub.service.BlogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * @author mingchiuli
 * @create 2022-06-07 11:01 AM
 */
@Aspect
@Component
@Slf4j
public class BloomAspect {

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

    @Before("pt()")
    public void before(JoinPoint jp) {

        Signature signature = jp.getSignature();
        //方法名
        String methodName = signature.getName();
        //参数
        Object[] args = jp.getArgs();

        switch (methodName) {
            case "list":
                Integer i = (Integer) args[0];
                if (Boolean.FALSE.equals(redisTemplate.opsForValue().getBit(Const.BLOOM_FILTER_PAGE, i))) {
                    throw new ArithmeticException("没有此页！");
                }
                break;
            case "detail":
            case "getBlogStatus":
                Long l = (Long) args[0];
                if (Boolean.FALSE.equals(redisTemplate.opsForValue().getBit(Const.BLOOM_FILTER_BLOG, l))) {
                    throw new ArithmeticException("没有此博客！");
                }
                break;
            case "getCountByYear":
                int year = (Integer) args[0];
                int[] years = blogService.searchYears();
                for (int y : years) {
                    if (year == y) {
                        return;
                    }
                }
                throw new ArithmeticException("没有此年份！");
            case "listByYear":
                int currentPage = (Integer) args[0];
                int yearMark = (Integer) args[1];
                if (Boolean.FALSE.equals(redisTemplate.opsForValue().getBit(Const.BLOOM_FILTER_PAGE + yearMark, currentPage))) {
                    throw new ArithmeticException("没有此页面！");
                }
                break;
        }
    }
}
