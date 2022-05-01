package com.markerhub.log.mq;

import com.markerhub.config.RabbitConfig;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
    public void processMessage(String msg, Channel channel, Message message) {
        simpMessagingTemplate.convertAndSend("/logs/log", msg);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        //手动签收消息
        //false代表不是批量签收模式
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            //出现异常说明网络中断
            log.info("rabbitmq处理后台日志投递消息确认出现异常", e);
        }

        //退货，重新入队列
//        channel.basicNack(deliveryTag, false, true);
    }
}
