package com.markerhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Result;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthenticationFilter extends BasicAuthenticationFilter {

	ObjectMapper objectMapper;

	JwtUtils jwtUtils;

	UserService sysUserService;

	RedisTemplate<String, Object> redisTemplate;

	@Autowired
	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Autowired
	public void setJwtUtils(JwtUtils jwtUtils) {
		this.jwtUtils = jwtUtils;
	}

	@Autowired
	public void setSysUserService(UserService sysUserService) {
		this.sysUserService = sysUserService;
	}

	@Autowired
	public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public JwtAuthenticationFilter(AuthenticationManager authenticationManager) {
		super(authenticationManager);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

		String jwt = request.getHeader(jwtUtils.getHeader());
		if (!StringUtils.hasLength(jwt)) {
			chain.doFilter(request, response);
			return;
		}

		Authentication authentication;

		try {
			authentication = getAuthentication(jwt);
		} catch (Exception e) {
			response.setContentType("application/json;charset=utf-8");
			response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, e.getMessage(), null)));
			return;
		}

		//非白名单资源、接口都要走这个流程，没有set就不能访问
		SecurityContextHolder.getContext().setAuthentication(authentication);

		chain.doFilter(request, response);
	}

	private Authentication getAuthentication(String jwt) {
		Claims claim = jwtUtils.getClaimByToken(jwt);
		if (claim == null) {
			throw new JwtException("token异常，请重新登录");
		}
		if (jwtUtils.isTokenExpired(claim.getExpiration())) {
			throw new JwtException("token已过期，请重新登录");
		}

		String username = claim.getSubject();
		return new PreAuthenticatedAuthenticationToken(username, null, AuthorityUtils.createAuthorityList(sysUserService.getUserRole(username).toArray(new String[0])));
	}
}
