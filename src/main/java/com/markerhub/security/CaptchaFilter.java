package com.markerhub.security;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.markerhub.common.exception.CaptchaException;
import com.markerhub.common.lang.Const;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CaptchaFilter extends OncePerRequestFilter {


	RedisTemplate<String, Object> redisTemplate;

	LoginFailureHandler loginFailureHandler;

	@Autowired
	public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Autowired
	public void setLoginFailureHandler(LoginFailureHandler loginFailureHandler) {
		this.loginFailureHandler = loginFailureHandler;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {

		String url = httpServletRequest.getRequestURI();

		if ("/login".equals(url) && httpServletRequest.getMethod().equals("POST")) {
			// 校验验证码
			validate(httpServletRequest);
		}
		filterChain.doFilter(httpServletRequest, httpServletResponse);
	}

	// 校验验证码逻辑
	private void validate(HttpServletRequest httpServletRequest) {

		String code = httpServletRequest.getParameter("code");
		String key = httpServletRequest.getParameter("key");

		if (StringUtils.isBlank(code) || StringUtils.isBlank(key)) {
			throw new CaptchaException("验证码无效");
		}

		if (!code.equals(redisTemplate.opsForValue().get(Const.CAPTCHA_KEY + key))) {
			redisTemplate.delete(Const.CAPTCHA_KEY + key);
			throw new CaptchaException("验证码错误");
		}

	}
}
