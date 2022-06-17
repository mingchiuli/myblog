package com.markerhub.config;

import com.markerhub.interceptor.WebSocketInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
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

    WebSocketInterceptor webSocketInterceptor;

    @Autowired
    public void setWebsocketInterceptor(WebSocketInterceptor webSocketInterceptor) {
        this.webSocketInterceptor = webSocketInterceptor;
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
        registration.interceptors(webSocketInterceptor);
    }


}
