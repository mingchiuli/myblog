package com.markerhub.config;

import com.markerhub.security.*;
import com.markerhub.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;


@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    LoginFailureHandler loginFailureHandler;

    LoginSuccessHandler loginSuccessHandler;

    CaptchaFilter captchaFilter;

    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    JwtLogoutSuccessHandler jwtLogoutSuccessHandler;

    RedisTemplate<String, Object> redisTemplate;

    UserService sysUserService;

    AuthenticationConfiguration authenticationConfiguration;

    UserDetailsService userDetailsService;

    public SecurityConfig(LoginFailureHandler loginFailureHandler, LoginSuccessHandler loginSuccessHandler, CaptchaFilter captchaFilter, JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint, JwtLogoutSuccessHandler jwtLogoutSuccessHandler, RedisTemplate<String, Object> redisTemplate, UserService sysUserService, AuthenticationConfiguration authenticationConfiguration, UserDetailsService userDetailsService) {
        this.loginFailureHandler = loginFailureHandler;
        this.loginSuccessHandler = loginSuccessHandler;
        this.captchaFilter = captchaFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtLogoutSuccessHandler = jwtLogoutSuccessHandler;
        this.redisTemplate = redisTemplate;
        this.sysUserService = sysUserService;
        this.authenticationConfiguration = authenticationConfiguration;
        this.userDetailsService = userDetailsService;
    }

    private static final String[] URL_WHITELIST = {
            "/captcha",
            "/favicon.ico",
            "/blogsByYear/**",
            "/getCountByYear/**",
            "/blogs/**",
            "/blog/**",
            "/blogStatus/**",
            "/searchYears",
            "/searchByYear/**",
            "/search/**",
            "/blogToken/**",
            "/getJWT",
            "/addWebsite",
            "/searchRecent/**",
            "/searchWebsite/**",
            "/sysLog/**",
            "/cooperate/**",
            "/upload/img/**"

    };

    @Bean
    public SecurityFilterChain configure(HttpSecurity http) throws Exception {

        http.cors().and().csrf().disable()

                // 登录配置
                .formLogin()
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)

                .and()
                .logout()
                .logoutSuccessHandler(jwtLogoutSuccessHandler)

                // 禁用session
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)


                // 配置拦截规则
                .and()
                .authorizeRequests()
                .antMatchers(URL_WHITELIST).permitAll()
                .anyRequest().authenticated()

                // 异常处理器
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
//                .accessDeniedHandler()配置权限失败的处理器，因为权限失败会被全局异常捕获，不走这个逻辑，所以想要启用需要关闭全局异常捕获的相关异常逻辑

                // 配置自定义的过滤器
                .and()
                .addFilter(jwtAuthenticationFilter())
                .addFilterBefore(captchaFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter(), LogoutFilter.class)
                .userDetailsService(userDetailsService);

        return http.build();

    }

    //多次调用方法只生成一个Bean实例
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() throws Exception {
        return new JwtAuthenticationFilter(authenticationManager());
    }

    @Bean
    public AuthenticationManager authenticationManager() throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

}
