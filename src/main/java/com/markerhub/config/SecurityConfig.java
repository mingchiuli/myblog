package com.markerhub.config;

import com.markerhub.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;


@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    LoginFailureHandler loginFailureHandler;

    LoginSuccessHandler loginSuccessHandler;

    CaptchaFilter captchaFilter;

    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    UserDetailServiceImpl userDetailService;

    JwtLogoutSuccessHandler jwtLogoutSuccessHandler;

    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    @Lazy
    public void setJwtAuthenticationFilter(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Autowired
    public void setLoginFailureHandler(LoginFailureHandler loginFailureHandler) {
        this.loginFailureHandler = loginFailureHandler;
    }
    @Autowired
    public void setLoginSuccessHandler(LoginSuccessHandler loginSuccessHandler) {
        this.loginSuccessHandler = loginSuccessHandler;
    }

    @Autowired
    public void setCaptchaFilter(CaptchaFilter captchaFilter) {
        this.captchaFilter = captchaFilter;
    }

    @Autowired
    public void setJwtAuthenticationEntryPoint(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Autowired
    public void setUserDetailService(UserDetailServiceImpl userDetailService) {
        this.userDetailService = userDetailService;
    }

    @Autowired
    public void setJwtLogoutSuccessHandler(JwtLogoutSuccessHandler jwtLogoutSuccessHandler) {
        this.jwtLogoutSuccessHandler = jwtLogoutSuccessHandler;
    }



    @Bean
    BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
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


    protected void configure(HttpSecurity http) throws Exception {

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
                .addFilter(jwtAuthenticationFilter)
                .addFilterBefore(captchaFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, LogoutFilter.class);

    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() throws Exception {
        return new JwtAuthenticationFilter(authenticationManager());
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailService);
    }

    @Bean
    public SecurityContextLogoutHandler securityContextLogoutHandler() {
        return new SecurityContextLogoutHandler();
    }
}