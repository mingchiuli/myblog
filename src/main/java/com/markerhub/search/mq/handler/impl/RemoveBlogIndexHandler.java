package com.markerhub.search.mq.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.mapper.BlogMapper;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.BlogIndexEnum;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.search.mq.handler.BlogIndexHandler;
import com.markerhub.service.BlogService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

@Component
@Slf4j
public class RemoveBlogIndexHandler implements BlogIndexHandler {

    BlogMapper blogMapper;

    @Autowired
    public void setBlogMapper(BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
    }

    ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    BlogService blogService;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    public void setElasticsearchRestTemplate(ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }

    @Override
    public boolean supports(BlogIndexEnum blogIndexEnum) {
        return BlogIndexEnum.REMOVE.equals(blogIndexEnum);
    }

    @Override
    @SneakyThrows
    public void handle(PostMQIndexMessage message, Channel channel, Message msg) {
        String removeUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");

        if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + removeUUID))) {
            Long deleteId = message.getPostId();

            /*
             * Redis流程
             */

            StringBuilder builder = new StringBuilder();
            builder.append("::");
            builder.append(deleteId);

            String contentPrefix = Const.HOT_BLOG + "::BlogController::detail" + builder;
            String statusPrefix = Const.BLOG_STATUS + "::BlogController::getBlogStatus" + builder;

            ArrayList<String> list = new ArrayList<>();
            list.add(contentPrefix);
            list.add(statusPrefix);

            String luaContentScript = "local current = redis.call('get', KEYS[1]);" +
                    "if current == false then " +
                    "    return nil;" +
                    "end " +
                    "return redis.call('del', KEYS[1]);";

            String luaStatusScript = "local current = redis.call('get', KEYS[2]);" +
                    "if current == false then " +
                    "    return nil;" +
                    "end " +
                    "return redis.call('del', KEYS[2]);";

            redisTemplate.execute(new DefaultRedisScript<>(luaContentScript, Long.class), list);
            redisTemplate.execute(new DefaultRedisScript<>(luaStatusScript, Long.class), list);

            Set<String> keys = redisTemplate.keys(Const.HOT_BLOGS_PREFIX);

            if (keys != null) {
                redisTemplate.unlink(keys);
            }

            log.info("删除博客Redis删除完毕");


            /*
             * ES流程
             */

            String delete = elasticsearchRestTemplate.delete(deleteId.toString(), BlogPostDocument.class);
            log.info("ES删除{}号结果: {}", deleteId, delete);

            long deliveryTagRemove = msg.getMessageProperties().getDeliveryTag();
            //手动签收消息
            //false代表不是批量签收模式
            try {
                channel.basicAck(deliveryTagRemove, false);
                redisTemplate.delete(Const.CONSUME_MONITOR + removeUUID);
            } catch (IOException e) {
                log.info("rabbitmq处理ES删除消息确认出现异常", e);
            }
        } else {
            long deliveryTag = msg.getMessageProperties().getDeliveryTag();
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
