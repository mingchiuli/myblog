package com.markerhub.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.common.lang.Const;
import com.markerhub.common.vo.StompPrincipal;
import com.markerhub.entity.User;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * @author mingchiuli
 * @create 2021-12-21 11:11 AM
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{

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

    JwtUtil jwtUtil;

    @Autowired
    public void setJwtUtils(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/cooperate", "/sysLog").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        //客户端向服务器发消息的前缀
        registry.setApplicationDestinationPrefixes("/app");
        //客户端订阅消息的前缀
        registry.enableSimpleBroker("/topic", "/queue", "/user", "/logs");
        //用户级别订阅消息的前缀(默认已经配了/user)
//        registry.setUserDestinationPrefix("/user");

    }

    //握手拦截器
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        String token = accessor.getFirstNativeHeader("Authorization");
                        //验证token是否有效
                        Claims claim = jwtUtil.getClaimByToken(token);
                        if (claim == null || jwtUtil.isTokenExpired(claim.getExpiration())) {
                            throw new AuthenticationException("token验证失败");
                        }

                        String username = claim.getSubject();

                        String id = userService.getOne(new QueryWrapper<User>().select("id").eq("username", username)).getId().toString();

                        String role;

                        String profileRole =  (String) redisTemplate.opsForValue().get(Const.ROLE_PREFIX + id);

                        if (profileRole != null) {
                            role = profileRole;
                        } else {
                            role = userService.getOne(new QueryWrapper<User>().select("role").eq("id", id)).getRole();
                        }

                        if (!(Const.ADMIN.equals(role) || Const.BOY.equals(role) || Const.GIRL.equals(role))) {
                            throw new AuthenticationException("禁止进入编辑室");
                        }

                        accessor.setUser(new StompPrincipal(id));
                    }
                    return message;
                }
                return null;
            }

        });

    }
}
