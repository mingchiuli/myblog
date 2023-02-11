package com.markerhub.cooperate.mq;

import com.markerhub.utils.SpringUtils;
import com.markerhub.cooperate.dto.MessageDto;
import com.markerhub.cooperate.mq.handler.WSHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
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
    SimpMessagingTemplate simpMessagingTemplate;

    public WSMessageHandler(RedisTemplate<String, Object> redisTemplate, SimpMessagingTemplate simpMessagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Bean("WSMessageListener")
    MessageListenerAdapter wSMessageListener(WSMessageHandler wSMessageHandler) {
        return new MessageListenerAdapter(wSMessageHandler, "processMessage");
    }

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


    @SuppressWarnings("unused")
    public void processMessage(MessageDto msg) {
        for (WSHandler handler : CacheHandlers.cacheHandlers.values()) {
            if (handler.supports(msg)) {
                handler.handle(msg);
                break;
            }
        }
    }
}
