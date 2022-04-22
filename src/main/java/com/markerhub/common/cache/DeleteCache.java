package com.markerhub.common.cache;

import java.lang.annotation.*;

/**
 * @author mingchiuli
 * @create 2022-04-22 3:01 PM
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeleteCache {

    String[] name() default "";
}
