package com.markerhub.security;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.exception.CaptchaException;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
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

	ObjectMapper objectMapper;

	RedisTemplate<String, Object> redisTemplate;

	LoginFailureHandler loginFailureHandler;


	public CaptchaFilter(ObjectMapper objectMapper, RedisTemplate<String, Object> redisTemplate, LoginFailureHandler loginFailureHandler) {
		this.objectMapper = objectMapper;
		this.redisTemplate = redisTemplate;
		this.loginFailureHandler = loginFailureHandler;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

		String url = request.getRequestURI();

		if ("/login".equals(url) && request.getMethod().equals("POST")) {
			// 校验验证码
			try {
				validate(request);
			} catch (CaptchaException e) {
				response.setContentType("application/json;charset=utf-8");
				response.getWriter().write(objectMapper.writeValueAsString(Result.fail(400, e.getMessage(), null)));
				return;
			}

		}
		filterChain.doFilter(request, response);
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
