package com.markerhub.search.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.config.RabbitConfig;
import com.markerhub.entity.Blog;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.service.BlogService;
import com.markerhub.util.MyUtil;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * @author mingchiuli
 * @create 2021-12-13 11:38 AM
 */
@Slf4j
@Component
@RabbitListener(queues = RabbitConfig.ES_QUEUE, concurrency = "1-2")
public class BlogMessageHandler {

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

    @SneakyThrows
    @RabbitHandler
    public void handler(PostMQIndexMessage message, Channel channel, Message msg) {
        switch (message.getType()) {
            case PostMQIndexMessage.UPDATE:

                String updateUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");

                if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + updateUUID))) {
                    Long updateId = message.getPostId();
                    Blog blogExisted = blogService.getById(updateId);
                    BlogPostDocument postDocument = MyUtil.blogToDocument(blogExisted);

                    ObjectMapper objectMapper = new ObjectMapper();
                    String obj = objectMapper.writeValueAsString(postDocument);
                    Document document = Document.parse(obj);

                    UpdateQuery query = UpdateQuery
                            .builder(String.valueOf(updateId))
                            .withDocument(document)
                            .build();

                    IndexCoordinates indexCoordinates = elasticsearchRestTemplate.getIndexCoordinatesFor(BlogPostDocument.class);
                    UpdateResponse update = elasticsearchRestTemplate.update(query, indexCoordinates);
                    String result = String.valueOf(update.getResult());
                    log.info("ES更新{}号结果: {}", updateId, result);

                    long deliveryTagUpdate = msg.getMessageProperties().getDeliveryTag();

                    //手动签收消息，防止宕机导致消息丢失
                    //false代表不是批量签收模式
                    try {
                        channel.basicAck(deliveryTagUpdate, false);
                        //消费成功就删除redis的值
                        redisTemplate.delete(Const.CONSUME_MONITOR + updateUUID);
                    } catch (IOException e) {
                        log.info("rabbitmq处理ES更新消息确认出现异常", e);
                    }
                } else {
                    //如果redis里没有保存，说明已经消费了，就直接将这个消息丢弃掉，且不入队
                    long deliveryTag = msg.getMessageProperties().getDeliveryTag();
                    channel.basicNack(deliveryTag, false, false);
                }
                break;

            case PostMQIndexMessage.REMOVE:

                String removeUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");

                if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + removeUUID))) {
                    Long deleteId = message.getPostId();
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
                break;
            case PostMQIndexMessage.CREATE:

                String createUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");
                if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + createUUID))) {
                    Long createId = message.getPostId();
                    Blog newBlog = blogService.getById(createId);
                    BlogPostDocument newDocument = MyUtil.blogToDocument(newBlog);
                    BlogPostDocument save = elasticsearchRestTemplate.save(newDocument);

                    log.info("ES创建{}号结果: {}", createId, save);

                    long deliveryTagCreate = msg.getMessageProperties().getDeliveryTag();
                    //手动签收消息
                    //false代表不是批量签收模式
                    try {
                        channel.basicAck(deliveryTagCreate, false);
                        redisTemplate.delete(Const.CONSUME_MONITOR + createUUID);
                    } catch (IOException e) {
                        log.info("rabbitmq处理ES创建消息确认出现异常", e);
                    }
                } else {
                    long deliveryTag = msg.getMessageProperties().getDeliveryTag();
                    channel.basicNack(deliveryTag, false, false);
                }
                break;
        }
    }
}
