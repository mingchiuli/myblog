package com.markerhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 如果访问白名单没有配置的url
 * 和SecurityContextHolder.getContext().setAuthentication(token);
 * 就会跳转到这个处理逻辑
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	ObjectMapper objectMapper;

	@Autowired
	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		ServletOutputStream outputStream = response.getOutputStream();

		Result result = Result.fail(authException.getMessage());

		outputStream.write(objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8));

		outputStream.flush();
		outputStream.close();
	}
}
