package com.markerhub.security;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.UserEntity;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import com.markerhub.utils.MyUtils;
import org.springframework.beans.factory.annotation.Autowired;
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


@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	JwtUtils jwtUtils;

	@Autowired
	public void setJwtUtils(JwtUtils jwtUtils) {
		this.jwtUtils = jwtUtils;
	}

	UserService userService;

	@Autowired
	public void setUserService(UserService userService) {
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

		MyUtils.setUserToCache(jwt, user, (long) (5 * 60));

		Result succ = Result.succ(MapUtil.builder()
				.put("user", user)
				.put("token", jwt)
				.map());


		outputStream.write(JSONUtil.toJsonStr(succ).getBytes(StandardCharsets.UTF_8));

		outputStream.flush();
		outputStream.close();
	}

}
