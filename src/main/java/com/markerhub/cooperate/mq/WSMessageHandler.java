package com.markerhub.cooperate.mq;

import com.markerhub.utils.SpringUtils;
import com.markerhub.cooperate.dto.MessageDto;
import com.markerhub.cooperate.mq.handler.WSHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
public class WSMessageHandler {

    private static class CacheHandlers {
        private static final Map<String, WSHandler> cacheHandlers = SpringUtils.getHandlers(WSHandler.class);
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }


    @Bean("WSMessageListener")
    //processMessage作为listener
    MessageListenerAdapter wSMessageListener(WSMessageHandler wSMessageHandler) {
        return new MessageListenerAdapter(wSMessageHandler, "processMessage");
    }

    //在container内将queue和listener绑定
    @Bean("WSMessageListenerContainer")
    SimpleMessageListenerContainer wSMessageListenerContainer(ConnectionFactory connectionFactory,
                                                                     @Qualifier("WSMessageListener") MessageListenerAdapter listenerAdapter,
                                                                     @Qualifier("WS_QUEUE") Queue queue) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setMessageListener(listenerAdapter);
        return container;
    }


    public void processMessage(MessageDto msg) {
        for (WSHandler handler : CacheHandlers.cacheHandlers.values()) {
            if (handler.supports(msg)) {
                handler.handle(msg);
                break;
            }
        }
    }
}
