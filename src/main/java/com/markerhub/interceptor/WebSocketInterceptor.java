package com.markerhub.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.common.lang.Const;
import com.markerhub.common.vo.StompPrincipal;
import com.markerhub.entity.UserEntity;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * @author mingchiuli
 * @create 2022-06-17 9:46 PM
 */
@Component
public class WebSocketInterceptor implements ChannelInterceptor {

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    JwtUtils jwtUtils;

    @Autowired
    public void setJwtUtils(JwtUtils jwtUtils) {
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
                String id = user.getId().toString();
                String role = user.getRole();

                if (!(Const.ADMIN.equals(role) || Const.BOY.equals(role) || Const.GIRL.equals(role))) {
                    throw new AuthenticationException("禁止进入编辑室");
                }

                accessor.setUser(new StompPrincipal(id));
            }
            return message;
        }
        return null;
    }
}
