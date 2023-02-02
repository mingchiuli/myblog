package com.markerhub.config;

import com.markerhub.common.lang.Const;
import com.markerhub.search.mq.BlogIndexEnum;
import com.markerhub.search.mq.PostMQIndexMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import javax.annotation.PostConstruct;
import java.util.UUID;

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

    public static String serverMark;

    public static final String ES_QUEUE = "ex_queue";
    public static final String ES_EXCHANGE = "ex_exchange";
    public static final String ES_BINDING_KEY = "ex_exchange";

    public static String WS_QUEUE = "wx_queue_";
    public static final String WS_TOPIC_EXCHANGE = "wx_topic_exchange";
    public static final String WS_FANOUT_EXCHANGE = "wx_fanout_exchange";
    public static final String WS_BINDING_KEY = "wx_exchange_";


    public static final String LOG_QUEUE = "log_queue";
    public static final String LOG_EXCHANGE = "log_exchange";
    public static final String LOG_BINDING_KEY = "log_exchange";

    RedisTemplate<String, Object> redisTemplate;

    RabbitTemplate rabbitTemplate;

    public RabbitConfig(RedisTemplate<String, Object> redisTemplate, RabbitTemplate rabbitTemplate) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Bean("WS_QUEUE")
    public Queue wsQueue() {
        serverMark = UUID.randomUUID().toString();
        WS_QUEUE += serverMark;
        return new Queue(WS_QUEUE, true, false, true);
    }

    @Bean("WS_FANOUT_EXCHANGE")
    public FanoutExchange wsfExchange() {
        return new FanoutExchange(WS_FANOUT_EXCHANGE);
    }

    @Bean
    public Binding wsFanoutBinding(@Qualifier("WS_QUEUE") Queue wsQueue, @Qualifier("WS_FANOUT_EXCHANGE") FanoutExchange wsExchange) {
        return BindingBuilder.bind(wsQueue).to(wsExchange);
    }

    //ES交换机
    @Bean("WS_TOPIC_EXCHANGE")
    public TopicExchange wsExchange() {
        return new TopicExchange(WS_TOPIC_EXCHANGE);
    }

    //绑定ES队列和ES交换机
    @Bean
    public Binding wsTopicBinding(@Qualifier("WS_QUEUE") Queue wsQueue, @Qualifier("WS_TOPIC_EXCHANGE") TopicExchange wsExchange) {
        return BindingBuilder.bind(wsQueue).to(wsExchange).with(WS_BINDING_KEY + serverMark);
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
    @PostConstruct
    public void initRabbitTemplate() {
        //设置抵达broker服务器的回掉
        //CorrelationData correlationData, boolean ack, String cause
        //当前消息的唯一关联数据、服务器对消息是否成功收到、失败的原因
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.info("存在消息抵达broker服务器失败的回掉{}, {}", correlationData, cause);
            }

            if (!ack && correlationData != null && Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + correlationData.getId()))) {
                log.info("抵达broker服务器失败的回掉{}, {}", correlationData, cause);
                String uuid = correlationData.getId();
                String str = (String) redisTemplate.opsForValue().get(Const.CONSUME_MONITOR + uuid);
                if (str != null) {
                    //说明是日志消息的失败发送
                    String[] s = str.split("_");
                    String method = s[0];
                    String id = s[1];
                    //如果服务器没有收到，就重发
                    CorrelationData newCorrelationData = new CorrelationData();
                    redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + newCorrelationData.getId(), method + "_" + id);
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.ES_EXCHANGE,
                            RabbitConfig.ES_BINDING_KEY,
                            new PostMQIndexMessage(Long.valueOf(id), BlogIndexEnum.valueOf(method)), newCorrelationData);
                    //删除之前的键
                    redisTemplate.delete(Const.CONSUME_MONITOR + uuid);
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
        rabbitTemplate.setReturnsCallback(returned -> log.info("消息失败投递指定队列的回调{}", returned));


    }

    @Bean
    public Binding LogBinding(@Qualifier("LOG_QUEUE") Queue logQueue, @Qualifier("LOG_EXCHANGE") DirectExchange logExchange) {
        return BindingBuilder.bind(logQueue).to(logExchange).with(LOG_BINDING_KEY);
    }

}
