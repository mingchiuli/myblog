package com.markerhub.shiro;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.User;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtil;
import com.markerhub.util.MyUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;



/**
 * @author mingchiuli
 * @create 2021-10-27 5:44 PM
 */
@Component
@Slf4j
public class AccountRealm extends AuthorizingRealm {

    UserService userService;

    @Autowired
    @Lazy
    private void setUserServiceImpl(UserService userService) {
        this.userService = userService;
    }


    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private void setRedisTemplateImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    JwtUtil jwtUtil;

    @Autowired
    public void setJwtUtil(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {

        AccountProfile accountProfile = (AccountProfile)principals.getPrimaryPrincipal();
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        Long id = accountProfile.getId();


        String role = (String) redisTemplate.opsForValue().get(Const.ROLE_PREFIX + id);

        if (role == null) {
            role = (String) userService.getBaseMapper().selectObjs(new QueryWrapper<User>().select("role").eq("id", id)).get(0);
            redisTemplate.opsForValue().set(Const.ROLE_PREFIX + id, role, 10, TimeUnit.MINUTES);

        }

        info.addRole(role);

        return info;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {

        JwtToken jwtToken = (JwtToken) token;

        Claims claim = jwtUtil.getClaimByToken((String) jwtToken.getCredentials());
        String userId = claim.getSubject();

        User user;

        LinkedHashMap<String, Object> userInfo = (LinkedHashMap<String, Object>) redisTemplate.opsForHash().get(Const.USER_PREFIX + userId, Const.USER_OBJECT);
        if (userInfo != null) {
            user = MyUtil.jsonToObj(userInfo, User.class);
        } else {
            user = userService.getById(Long.valueOf(userId));
        }


        if (user == null) {
            throw new AuthenticationException("验证失败，请重新登录");
        }

        String originToken = (String) redisTemplate.opsForHash().get(Const.USER_PREFIX + userId, Const.TOKEN);

        if (originToken == null) {
            throw new AuthenticationException("密钥异常，请重新登录");
        }


        AccountProfile profile = new AccountProfile();

        BeanUtil.copyProperties(user, profile);

        return new SimpleAuthenticationInfo(profile, originToken, getName());
    }
}
