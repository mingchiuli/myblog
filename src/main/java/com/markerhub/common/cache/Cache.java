package com.markerhub.common.cache;

import java.lang.annotation.*;

/**
 * @author mingchiuli
 * @create 2021-12-01 7:45 AM
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cache {

    long expire() default 120 * 1000;

    String name() default "";

}
