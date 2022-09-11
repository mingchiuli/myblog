package com.markerhub.ws.mq;

import com.markerhub.common.vo.Content;
import com.markerhub.common.vo.Message;
import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.ws.mq.dto.Container;
import com.markerhub.ws.mq.dto.impl.InitOrDestroyMessageDto;
import com.markerhub.ws.mq.dto.MessageDto;
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
import java.util.ArrayList;

@Slf4j
@Component
public class WSMessageHandler {

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

        String methodName = msg.getMethodName();

        switch (methodName) {
            case "init":
            case "pushUser":
                Container<InitOrDestroyMessageDto.Bind> containerV1 = msg.getData();
                InitOrDestroyMessageDto.Bind dataV1 = containerV1.getData();
                String blogIdV1 = dataV1.getBlogId();
                ArrayList<UserEntityVo> usersV1 = dataV1.getUsers();
                simpMessagingTemplate.convertAndSendToUser(blogIdV1,"/topic/users", usersV1);
                break;
            case "taskOver":
                Container<String> containerV2 = msg.getData();
                String from = containerV2.getData();
                simpMessagingTemplate.convertAndSend("/topic/over", from);
                break;
            case "syncContent":
                Container<Content> containerV3 = msg.getData();
                Content content = containerV3.getData();
                simpMessagingTemplate.convertAndSend("/topic/content/" + content.getBlogId(), content);
                break;
            case "chat":
                Container<Message> containerV4 = msg.getData();
                Message message = containerV4.getData();
                String id = message.getBlogId();
                String to = message.getTo();
                simpMessagingTemplate.convertAndSendToUser(to, "/" + id + "/queue/chat", message);
                break;
            case "destroy":
                Container<InitOrDestroyMessageDto.Bind> containerV5 = msg.getData();
                InitOrDestroyMessageDto.Bind dataV2 = containerV5.getData();
                String blogIdV2 = dataV2.getBlogId();
                ArrayList<UserEntityVo> usersV2 = dataV2.getUsers();
                simpMessagingTemplate.convertAndSendToUser(blogIdV2,"/topic/popUser", usersV2);
                break;

        }

    }
}
