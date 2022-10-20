package com.markerhub.common.valid;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.entity.BlogEntity;
import com.markerhub.service.BlogService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class BlogAuthenticationConstraintValidator implements ConstraintValidator<BlogAuthentication, Long> {

    BlogService blogService;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    @Override
    public void initialize(BlogAuthentication constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Long blogId, ConstraintValidatorContext constraintValidatorContext) {
        return blogService.getOne(new QueryWrapper<BlogEntity>().select("status").eq("id", blogId)).getStatus() != 1;
    }
}
