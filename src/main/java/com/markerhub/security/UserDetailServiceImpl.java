package com.markerhub.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.UserEntity;
import com.markerhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Component
public class UserDetailServiceImpl implements UserDetailsService {

	RedisTemplate<String, Object> redisTemplate;

	UserService sysUserService;

	@Autowired
	public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Autowired
	public void setSysUserService(UserService sysUserService) {
		this.sysUserService = sysUserService;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		UserEntity sysUser = sysUserService.getOne(new QueryWrapper<UserEntity>().eq("username", username));

		if (sysUser == null) {
			throw new UsernameNotFoundException("用户名不正确");
		}

		//通过User去自动比较用户名和密码
		return new User(sysUser.getUsername(), sysUser.getPassword(), true,true,true, sysUser.getStatus() == 0, getUserRole(sysUser.getUsername()));
	}



	/**
	 * 获取用户权限信息（角色权限）
	 * @param username 用户
	 * @return List<GrantedAuthority>
	 */
	private List<GrantedAuthority> getUserRole(String username){
		List<String> userRole = sysUserService.getUserRole(username);
		return AuthorityUtils.createAuthorityList(userRole.toArray(new String[0]));
	}
}
