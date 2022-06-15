package com.markerhub.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;


/**
 * @author mingchiuli
 * @create 2021-12-22 12:58 AM
 */

@Component
public class SpringUtils implements ApplicationContextAware {

    private static ApplicationContext applicationContext;


    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {

        SpringUtils.applicationContext = applicationContext;

    }


    public ApplicationContext getApplicationContext(){

        return applicationContext;

    }


    public static Object getBean(String beanName){

        return applicationContext.getBean(beanName);

    }

    public static <T> T getBean(Class<T> clazz){

        return applicationContext.getBean(clazz);

    }
}
