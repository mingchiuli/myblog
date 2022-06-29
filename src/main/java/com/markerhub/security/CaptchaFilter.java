package com.markerhub.security;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.markerhub.common.exception.CaptchaException;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
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
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

		String url = request.getRequestURI();

		if ("/login".equals(url) && request.getMethod().equals("POST")) {
			// 校验验证码
			try {
				validate(request);
			} catch (CaptchaException e) {
				response.setContentType("application/json;charset=utf-8");
				response.getWriter().write(JSONUtil.toJsonStr(Result.fail(400, e.getMessage(), null)));
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
