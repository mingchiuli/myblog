package com.markerhub.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author mingchiuli
 * @create 2021-11-06 6:24 PM
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    /**
     * 文件上传处理，URL映射到本地磁盘路径
     * @param registry
     */

    @Value("${addResourceHandler}")
    private String addResourceHandler;

    @Value("${addResourceLocations}")
    private String addResourceLocations;


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //环境
        registry.addResourceHandler(addResourceHandler).addResourceLocations(addResourceLocations);
    }

    /**
     * 解决跨域问题
     * @param registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600)
                .allowedHeaders("*");
    }
}
