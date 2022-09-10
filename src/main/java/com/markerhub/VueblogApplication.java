package com.markerhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class VueblogApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(VueblogApplication.class, args);

//        for (String beanDefinitionName : applicationContext.getBeanDefinitionNames()) {
//            System.out.println(beanDefinitionName);
//        }


    }

}
