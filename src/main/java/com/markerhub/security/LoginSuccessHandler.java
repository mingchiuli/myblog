package com.markerhub.security;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.UserEntity;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;


@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	ObjectMapper objectMapper;
	JwtUtils jwtUtils;
	UserService userService;

	public LoginSuccessHandler(ObjectMapper objectMapper, JwtUtils jwtUtils, UserService userService) {
		this.objectMapper = objectMapper;
		this.jwtUtils = jwtUtils;
		this.userService = userService;
	}

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		ServletOutputStream outputStream = response.getOutputStream();

		// 生成jwt
		String jwt = jwtUtils.generateToken(authentication.getName());

		UserEntity user = userService.getOne(new QueryWrapper<UserEntity>().select("id", "username", "avatar", "email", "role").eq("username", authentication.getName()));
		userService.update(new UpdateWrapper<UserEntity>().set("last_login", LocalDateTime.now()).eq("username", authentication.getName()));

		HashMap<String, Object> map = new HashMap<>();
		map.put("user", user);
		map.put("token", jwt);

		Result success = Result.succ(map);


		outputStream.write(objectMapper.writeValueAsString(success).getBytes(StandardCharsets.UTF_8));

		outputStream.flush();
		outputStream.close();
	}

}
