package com.markerhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author mingchiuli
 * @create 2021-11-06 6:24 PM
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 解决跨域问题，经过测试，websocket和Spring Security的配置不兼容，采用原始配置，此时响应头无法添加Authorization，
     * 于是放到数据体中
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
//                .allowCredentials(true)
                .maxAge(3600);
//                .allowedHeaders("*");
    }
}
