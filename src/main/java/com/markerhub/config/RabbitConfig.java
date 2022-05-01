package com.markerhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.Blog;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.util.MyUtil;
import com.markerhub.util.SpringUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * RabbitMQ
 * 作用是同步ES
 * 和
 * 将日志输出到MQ
 *
 * @author mingchiuli
 * @create 2021-12-12 11:22 PM
 */
@Configuration
@Slf4j
public class RabbitConfig {

    public static final String ES_QUEUE = "ex_queue";
    public static final String ES_EXCHANGE = "ex_exchange";
    public static final String ES_BINDING_KEY = "ex_exchange";

    public static final String LOG_QUEUE = "log_queue";
    public static final String LOG_EXCHANGE = "log_exchange";
    public static final String LOG_BINDING_KEY = "log_exchange";

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    //ES队列
    @Bean("ES_QUEUE")
    public Queue esQueue() {
        return new Queue(ES_QUEUE);
    }

    //ES交换机
    @Bean("ES_EXCHANGE")
    public DirectExchange esExchange() {
        return new DirectExchange(ES_EXCHANGE);
    }

    //绑定ES队列和ES交换机
    @Bean
    public Binding esBinding(@Qualifier("ES_QUEUE") Queue esQueue, @Qualifier("ES_EXCHANGE") DirectExchange esExchange) {
        return BindingBuilder.bind(esQueue).to(esExchange).with(ES_BINDING_KEY);
    }

    //LOG队列
    @Bean("LOG_QUEUE")
    public Queue logQueue() {
        return new Queue(LOG_QUEUE);
    }

    //LOG交换机
    @Bean("LOG_EXCHANGE")
    public DirectExchange logExchange() {
        return new DirectExchange(LOG_EXCHANGE);
    }

    //绑定LOG队列和LOG交换机
    @Bean
    public Binding LogBinding(@Qualifier("LOG_QUEUE") Queue logQueue, @Qualifier("LOG_EXCHANGE") DirectExchange logExchange) {
        return BindingBuilder.bind(logQueue).to(logExchange).with(LOG_BINDING_KEY);
    }

    @PostConstruct
    public void initRabbitTemplate() {
        //设置抵达broker服务器的回掉
        //CorrelationData correlationData, boolean ack, String cause
        //当前消息的唯一关联数据、服务器对消息是否成功收到、失败的原因
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.info("抵达broker服务器失败的回掉{}, {}", correlationData, cause);
                if (correlationData != null) {
                    String uuid = correlationData.getId();
                    String str = (String) redisTemplate.opsForValue().get(Const.CONSUME_MONITOR + uuid);
                    if (str != null) {
                        //说明是日志消息的失败发送
                        String[] s = str.split("_");
                        String method = s[0];
                        String id = s[1];
                        //如果服务器没有收到，就重发
                        rabbitTemplate.convertAndSend(
                                RabbitConfig.ES_EXCHANGE,
                                RabbitConfig.ES_BINDING_KEY,
                                new PostMQIndexMessage(Long.valueOf(id), method));

                    }
                }
            }
        });

        //设置抵达消息队列的确认回调
        //只要消息没有投递给指定的队列，就触发这个失败回调
        //returned属于ReturnedMessage类
//        public class ReturnedMessage {
//
              //投递失败的详细信息
//            private final Message message;
//            //回复的状态码
//            private final int replyCode;
//            //回复的文本内容
//            private final String replyText;
//            //当时这个消息发送给哪个交换机
//            private final String exchange;
//            //当时这个消息用哪个路由键
//            private final String routingKey;
        rabbitTemplate.setReturnsCallback(returned -> {
            log.info("消息失败投递指定队列的回调{}", returned);
        });


    }
}
