package com.markerhub.shiro;

import cn.hutool.json.JSONUtil;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.User;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtil;
import com.markerhub.util.MyUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * @author mingchiuli
 * @create 2021-10-27 5:58 PM
 */
@Component
@Slf4j
public class JwtFilter extends AuthenticatingFilter {

    JwtUtil jwtUtil;

    @Autowired
    private void setJwtUtils(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    UserService userService;

    @Autowired
    @Lazy
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /**
     * 执行onAccessDenied时调用executeLogin方法，完成登陆逻辑
     * @param servletRequest
     * @param servletResponse
     * @return
     */
    @Override
    protected AuthenticationToken createToken(ServletRequest servletRequest, ServletResponse servletResponse) {
        // 获取 token
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String jwt = request.getHeader("Authorization");
        if(!StringUtils.hasLength(jwt)) {
            return null;
        }
        return new JwtToken(jwt);
    }

    @Override
    protected boolean onAccessDenied(ServletRequest servletRequest, ServletResponse servletResponse) throws Exception {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String token = request.getHeader("Authorization");

        if(!StringUtils.hasLength(token)) {
            return true;
        } else {
            // 教验jwt
            Claims claim = jwtUtil.getClaimByToken(token);
            // 判断是否已过期

            try {
                if (claim == null || jwtUtil.isTokenExpired(claim.getExpiration())) {
                    throw new AuthenticationException("登录信息已过期，请重新登录");
                }
            } catch (Exception e) {
                servletResponse.setContentType("application/json;charset=utf-8");
                servletResponse.getWriter().write(JSONUtil.toJsonStr(Result.fail(401, e.getMessage(), null)));
                return false;
            }

            String userId = Objects.requireNonNull(claim).getSubject();

            User user = userService.getById(userId);

            //判断是否要往缓存加，以及执行

            MyUtil.setUserToCache(token, user, (long) (15 * 60));

        }
        // 执行自动登录
        return executeLogin(servletRequest, servletResponse);
    }


    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        try {
            //处理登录失败的异常，在realm里抛异常会进入这里
            httpResponse.setContentType("application/json;charset=utf-8");
            Throwable throwable = e.getCause() == null ? e : e.getCause();
            Result r = Result.fail(401, throwable.getMessage(), null);
            String json = JSONUtil.toJsonStr(r);
            httpResponse.getWriter().write(json);
        } catch (IOException ignored) {
        }
        return false;
    }
    /**
     * 对跨域提供支持
     */
    @Override
    protected boolean preHandle(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletRequest httpServletRequest = WebUtils.toHttp(request);
        HttpServletResponse httpServletResponse = WebUtils.toHttp(response);
        httpServletResponse.setHeader("Access-control-Allow-Origin", httpServletRequest.getHeader("Origin"));
        httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS,PUT,DELETE");
        httpServletResponse.setHeader("Access-Control-Allow-Headers", httpServletRequest.getHeader("Access-Control-Request-Headers"));
        //ws新加，必须
        httpServletResponse.setHeader("Access-Control-Allow-Credentials","true");
        // 跨域时会首先发送一个OPTIONS请求，这里我们给OPTIONS请求直接返回正常状态
        if (httpServletRequest.getMethod().equals(RequestMethod.OPTIONS.name())) {
            httpServletResponse.setStatus(org.springframework.http.HttpStatus.OK.value());
            return false;
        }
        return super.preHandle(request, response);
    }
}
