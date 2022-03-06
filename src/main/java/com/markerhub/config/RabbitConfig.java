package com.markerhub.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
public class RabbitConfig {

    public static final String ES_QUEUE = "ex_queue";
    public static final String ES_EXCHANGE = "ex_exchange";
    public static final String ES_BINDING_KEY = "ex_exchange";

    public static final String LOG_QUEUE = "log_queue";
    public static final String LOG_EXCHANGE = "log_exchange";
    public static final String LOG_BINDING_KEY = "log_exchange";



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
}
