package com.markerhub.security;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.UserEntity;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import com.markerhub.utils.MyUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

public class JwtAuthenticationFilter extends BasicAuthenticationFilter {

	JwtUtils jwtUtils;

	UserDetailServiceImpl userDetailService;

	UserService sysUserService;

	RedisTemplate<String, Object> redisTemplate;

	@Autowired
	public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Autowired
	public void setJwtUtils(JwtUtils jwtUtils) {
		this.jwtUtils = jwtUtils;
	}

	@Autowired
	public void setUserDetailService(UserDetailServiceImpl userDetailService) {
		this.userDetailService = userDetailService;
	}

	@Autowired
	public void setSysUserService(UserService sysUserService) {
		this.sysUserService = sysUserService;
	}

	public JwtAuthenticationFilter(AuthenticationManager authenticationManager) {
		super(authenticationManager);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

		String jwt = request.getHeader(jwtUtils.getHeader());
		if (StrUtil.isBlankOrUndefined(jwt)) {
			chain.doFilter(request, response);
			return;
		}

		Authentication authentication = getAuthentication(jwt);

		if (authentication == null) {
			response.setContentType("application/json;charset=utf-8");
			response.getWriter().write(JSONUtil.toJsonStr(Result.fail(401, "你已被强制下线", null)));
			return;
		}

		//非白名单资源、接口都要走这个流程，没有set就不能访问
		SecurityContextHolder.getContext().setAuthentication(authentication);

		chain.doFilter(request, response);
	}

	private Authentication getAuthentication(String jwt) {
		Claims claim = jwtUtils.getClaimByToken(jwt);
		if (claim == null) {
			throw new JwtException("token异常");
		}
		if (jwtUtils.isTokenExpired(claim.getExpiration())) {
			throw new JwtException("token已过期");
		}

		String username = claim.getSubject();
		// 获取用户的权限等信息

		UserEntity user;

		HashSet<Object> set = new HashSet<>(2);

		set.add(Const.USER_OBJECT);
		set.add(Const.TOKEN);

		List<Object> multiGet = redisTemplate.opsForHash().multiGet(Const.USER_PREFIX + username, set);
		LinkedHashMap<String, Object> userInfo = (LinkedHashMap<String, Object>) multiGet.get(0);
		String originToken = (String) multiGet.get(1);


		if (StringUtils.hasLength(originToken) && !jwt.equals(originToken)) {
			return null;
		}


		if (userInfo != null) {
			user = MyUtils.jsonToObj(userInfo, UserEntity.class);
		} else {
			user = sysUserService.getOne(new QueryWrapper<UserEntity>().eq("username", username));
			MyUtils.setUserToCache(jwt, user, (long) (5 * 60));
		}


		return new UsernamePasswordAuthenticationToken(user.getUsername(), null, userDetailService.getUserRole(user.getId()));

	}
}
