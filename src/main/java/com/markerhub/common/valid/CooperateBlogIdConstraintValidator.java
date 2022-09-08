package com.markerhub.common.valid;

import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.common.lang.Const;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class CooperateBlogIdConstraintValidator implements ConstraintValidator<CooperateBlogId, Long> {

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void initialize(CooperateBlogId constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Long blogId, ConstraintValidatorContext constraintValidatorContext) {
        return redisTemplate.opsForHash().size(Const.CO_PREFIX + blogId) < 3;
    }
}
