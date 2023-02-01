package com.markerhub.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.entity.UserEntity;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * @author mingchiuli
 * @create 2022-06-17 9:46 PM
 */
@Component
public class WebSocketInterceptor implements ChannelInterceptor {

    RedisTemplate<String, Object> redisTemplate;

    UserService userService;
    JwtUtils jwtUtils;

    public WebSocketInterceptor(RedisTemplate<String, Object> redisTemplate, UserService userService, JwtUtils jwtUtils) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {

                String token = accessor.getFirstNativeHeader("Authorization");
                //验证token是否有效
                Claims claim = jwtUtils.getClaimByToken(token);
                if (claim == null || jwtUtils.isTokenExpired(claim.getExpiration())) {
                    throw new AuthenticationException("token验证失败");
                }

                String username = claim.getSubject();
                UserEntity user = userService.getOne(new QueryWrapper<UserEntity>().select("id", "role").eq("username", username));
                String role = user.getRole();

                accessor.setUser(new PreAuthenticatedAuthenticationToken(username,
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_" + role)));

            }
            return message;
        }
        return null;
    }
}
