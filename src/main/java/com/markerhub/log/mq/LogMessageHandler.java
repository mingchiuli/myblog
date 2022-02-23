package com.markerhub.log.mq;

import com.markerhub.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * logback日志内容塞到rabbitmq队列里
 * @author mingchiuli
 * @create 2022-01-03 8:56 PM
 */
@Slf4j
@Component
public class LogMessageHandler {

    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @RabbitListener(id = "log", queues = RabbitConfig.LOG_QUEUE, autoStartup = "false")
//    @RabbitListener(id = "log", queues = RabbitConfig.LOG_QUEUE)
    public void processMessage(String msg) {
        simpMessagingTemplate.convertAndSend("/logs/log", msg);
    }
}
