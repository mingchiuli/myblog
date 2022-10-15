package com.markerhub.search.mq;

import com.markerhub.config.RabbitConfig;
import com.markerhub.search.mq.handler.BlogIndexHandler;
import com.markerhub.utils.SpringUtils;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * @author mingchiuli
 * @create 2021-12-13 11:38 AM
 */
@Slf4j
@Component
@RabbitListener(queues = RabbitConfig.ES_QUEUE)
public class BlogMessageHandler {

    private static class CacheHandlers {
        private static final Map<String, BlogIndexHandler> cacheHandlers = SpringUtils.getHandlers(BlogIndexHandler.class);
    }

    @RabbitHandler
    public void handler(PostMQIndexMessage message, Channel channel, Message msg) {
        for (BlogIndexHandler handler : CacheHandlers.cacheHandlers.values()) {
            if (handler.methodName().equals(message.typeEnum)) {
                handler.doHand(message, channel, msg);
                break;
            }
        }

    }
}
