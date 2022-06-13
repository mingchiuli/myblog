package com.markerhub.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.User;
import com.markerhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailServiceImpl implements UserDetailsService {

	RedisTemplate<String, Object> redisTemplate;

	@Autowired
	public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	UserService sysUserService;

	@Autowired
	public void setSysUserService(UserService sysUserService) {
		this.sysUserService = sysUserService;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		User sysUser = sysUserService.getOne(new QueryWrapper<User>().eq("username", username));

		if (sysUser == null) {
			throw new UsernameNotFoundException("用户名不正确");
		}

		if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.USER_PREFIX + sysUser.getId())) && sysUser.getStatus() == 0) {
			throw new AuthenticationException("用户已登录");
		}

		boolean accountNonLocked = sysUser.getStatus() == 0;

		//通过AccountUser去比较用户名和密码
		return new AccountUser(sysUser.getUsername(), sysUser.getPassword(), true, true,true, accountNonLocked, getUserRole(sysUser.getId()));
	}

	/**
	 * 获取用户权限信息（角色权限）
	 * @param userId 用户id
	 * @return List<GrantedAuthority>
	 */
	public List<GrantedAuthority> getUserRole(Long userId){
		String role = sysUserService.getOne(new QueryWrapper<User>().select("role").eq("id", userId)).getRole();
		return AuthorityUtils.createAuthorityList(role);
	}
}
