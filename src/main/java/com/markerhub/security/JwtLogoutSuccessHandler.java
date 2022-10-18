package com.markerhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtLogoutSuccessHandler implements LogoutSuccessHandler {

	ObjectMapper objectMapper;

	JwtUtils jwtUtils;

	RedisTemplate<String, Object> redisTemplate;

	SecurityContextLogoutHandler securityContextLogoutHandler;

	@Autowired
	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Autowired
	@Lazy
	public void setSecurityContextLogoutHandler(SecurityContextLogoutHandler securityContextLogoutHandler) {
		this.securityContextLogoutHandler = securityContextLogoutHandler;
	}

	@Autowired
	public void setJwtUtils(JwtUtils jwtUtils) {
		this.jwtUtils = jwtUtils;
	}

	@Autowired
	public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

		if (authentication != null) {
			securityContextLogoutHandler.logout(request, response, authentication);
		}

		response.setContentType("application/json;charset=UTF-8");
		ServletOutputStream outputStream = response.getOutputStream();

		response.setHeader(jwtUtils.getHeader(), "");

		Result result = Result.succ("");

		outputStream.write(objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8));

		outputStream.flush();
		outputStream.close();
	}
}
