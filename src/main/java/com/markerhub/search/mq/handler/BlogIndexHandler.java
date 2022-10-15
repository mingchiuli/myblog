package com.markerhub.search.mq.handler;

import com.markerhub.search.mq.BlogIndexEnum;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;

public interface BlogIndexHandler {
    BlogIndexEnum methodName();

    void doHand(PostMQIndexMessage message, Channel channel, Message msg);
}
