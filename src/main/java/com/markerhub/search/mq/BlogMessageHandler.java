package com.markerhub.search.mq;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.config.RabbitConfig;
import com.markerhub.entity.Blog;
import com.markerhub.mapper.BlogMapper;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * @author mingchiuli
 * @create 2021-12-13 11:38 AM
 */
@Slf4j
@Component
@RabbitListener(queues = RabbitConfig.ES_QUEUE, concurrency = "1-2")
public class BlogMessageHandler {

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

    @SneakyThrows
    @RabbitHandler
    public void handler(PostMQIndexMessage message, Channel channel, Message msg) {
        switch (message.getType()) {
            case PostMQIndexMessage.UPDATE:

                String updateUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");

                if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + updateUUID))) {
                    Long updateId = message.getPostId();
                    Blog blogExisted = blogService.getById(updateId);

                    /*
                     Redis??????
                     */
                    //??????????????????????????????lua???????????????delete??????????????????Long????????????????????????????????????????????????
                    StringBuilder builder = new StringBuilder();
                    builder.append(objectMapper.writeValueAsString(updateId));
                    builder = new StringBuilder(Arrays.toString(DigestUtil.md5(builder.toString())));
                    String contentPrefix = Const.HOT_BLOG + "::BlogController::detail::" + builder;
                    redisTemplate.delete(contentPrefix);

                    //??????????????????????????????
                    Blog blog = blogMapper.selectById(updateId);
                    //??????
                    int year = blog.getCreated().getYear();
                    /*
                     * ??????????????????????????????????????????
                     */
                    long count = blogMapper.getPageCount(blog.getCreated().toString());
                    //??????????????????
                    count++;

                    long pageNo = count % Const.PAGE_SIZE == 0 ? count / Const.PAGE_SIZE : count / Const.PAGE_SIZE + 1;
                    StringBuilder sb = new StringBuilder();
                    sb.append(objectMapper.writeValueAsString(pageNo));
                    sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
                    String pagePrefix = Const.HOT_BLOGS + "::BlogController::list::" + sb;
                    redisTemplate.delete(pagePrefix);

                    /*
                     * ??????????????????????????????????????????
                     */
                    int countYear = blogMapper.getPageYearCount(blog.getCreated().toString(), year);
                    countYear++;

                    int pageYearNo = countYear % Const.PAGE_SIZE == 0 ? countYear / Const.PAGE_SIZE : countYear / Const.PAGE_SIZE + 1;
                    StringBuilder s = new StringBuilder();
                    s.append(objectMapper.writeValueAsString(pageYearNo));
                    s.append(objectMapper.writeValueAsString(year));
                    s = new StringBuilder(Arrays.toString(DigestUtil.md5(s.toString())));
                    String pageYearPrefix = Const.HOT_BLOGS + "::BlogController::listByYear::" + s;
                    redisTemplate.delete(pageYearPrefix);

                    log.info("????????????Redis????????????");

                    /*
                     * ES??????
                     */

                    BlogPostDocument postDocument = MyUtil.blogToDocument(blogExisted);

                    String obj = objectMapper.writeValueAsString(postDocument);
                    Document document = Document.parse(obj);

                    UpdateQuery query = UpdateQuery
                            .builder(String.valueOf(updateId))
                            .withDocument(document)
                            .build();

                    IndexCoordinates indexCoordinates = elasticsearchRestTemplate.getIndexCoordinatesFor(BlogPostDocument.class);
                    UpdateResponse update = elasticsearchRestTemplate.update(query, indexCoordinates);
                    String result = String.valueOf(update.getResult());
                    log.info("ES??????{}?????????: {}", updateId, result);

                    long deliveryTagUpdate = msg.getMessageProperties().getDeliveryTag();

                    //???????????????????????????????????????????????????
                    //false??????????????????????????????
                    try {
                        channel.basicAck(deliveryTagUpdate, false);
                        //?????????????????????redis??????
                        redisTemplate.delete(Const.CONSUME_MONITOR + updateUUID);
                    } catch (IOException e) {
                        log.info("rabbitmq??????ES??????????????????????????????", e);
                    }
                } else {
                    //??????redis??????????????????????????????????????????????????????????????????????????????????????????
                    long deliveryTag = msg.getMessageProperties().getDeliveryTag();
                    channel.basicNack(deliveryTag, false, false);
                }
                break;

            case PostMQIndexMessage.REMOVE:

                String removeUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");

                if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + removeUUID))) {
                    Long deleteId = message.getPostId();

                    /*
                     * Redis??????
                     */

                    StringBuilder builder = new StringBuilder();
                    builder.append(objectMapper.writeValueAsString(deleteId));
                    builder = new StringBuilder(Arrays.toString(DigestUtil.md5(builder.toString())));

                    String contentPrefix = Const.HOT_BLOG + "::BlogController::detail::" + builder;
                    String statusPrefix = Const.BLOG_STATUS + "::BlogController::getBlogStatus::" + builder;

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
                        redisTemplate.delete(keys);
                    }

                    log.info("????????????Redis????????????");


                    /*
                     * ES??????
                     */

                    String delete = elasticsearchRestTemplate.delete(deleteId.toString(), BlogPostDocument.class);
                    log.info("ES??????{}?????????: {}", deleteId, delete);

                    long deliveryTagRemove = msg.getMessageProperties().getDeliveryTag();
                    //??????????????????
                    //false??????????????????????????????
                    try {
                        channel.basicAck(deliveryTagRemove, false);
                        redisTemplate.delete(Const.CONSUME_MONITOR + removeUUID);
                    } catch (IOException e) {
                        log.info("rabbitmq??????ES??????????????????????????????", e);
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

                    /*
                    ???Redis??????
                     */
                    StringBuilder builder = new StringBuilder();
                    builder.append(objectMapper.writeValueAsString(createId));
                    builder = new StringBuilder(Arrays.toString(DigestUtil.md5(builder.toString())));

                    String contentPrefix = Const.HOT_BLOG + "::BlogController::detail::" + builder;
                    String statusPrefix = Const.BLOG_STATUS + "::BlogController::getBlogStatus::" + builder;

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
                        redisTemplate.delete(keys);
                    }

                    log.info("????????????Redis????????????");


                    /*
                    ES??????
                     */

                    Blog newBlog = blogService.getById(createId);
                    BlogPostDocument newDocument = MyUtil.blogToDocument(newBlog);
                    BlogPostDocument save = elasticsearchRestTemplate.save(newDocument);

                    log.info("ES??????{}?????????: {}", createId, save);

                    long deliveryTagCreate = msg.getMessageProperties().getDeliveryTag();
                    //??????????????????
                    //false??????????????????????????????
                    try {
                        channel.basicAck(deliveryTagCreate, false);
                        redisTemplate.delete(Const.CONSUME_MONITOR + createUUID);
                    } catch (IOException e) {
                        log.info("rabbitmq??????ES??????????????????????????????", e);
                    }
                } else {
                    long deliveryTag = msg.getMessageProperties().getDeliveryTag();
                    channel.basicNack(deliveryTag, false, false);
                }
                break;
        }
    }
}
