package com.markerhub.search.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.config.RabbitConfig;
import com.markerhub.entity.BlogEntity;
import com.markerhub.mapper.BlogMapper;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.service.BlogService;
import com.markerhub.utils.MyUtils;
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
                    BlogEntity blogExisted = blogService.getById(updateId);

                    /*
                     Redis流程
                     */
                    //删除博客的缓存（不用lua脚本也行，delete返回值是一个Long，加锁博客缓存中不存在也可以删）
                    StringBuilder builder = new StringBuilder();
                    builder.append("::");
                    builder.append(objectMapper.writeValueAsString(updateId));
//                    builder = new StringBuilder(Arrays.toString(DigestUtil.md5(builder.toString())));
                    String contentPrefix = Const.HOT_BLOG + "::BlogController::detail" + builder;
                    redisTemplate.delete(contentPrefix);

                    //删除博客所在页的缓存
                    BlogEntity blog = blogMapper.selectById(updateId);
                    //年份
                    int year = blog.getCreated().getYear();
                    /*
                     * 查出普通分页这条博客在第几页
                     */
                    long count = blogMapper.getPageCount(blog.getCreated().toString());
                    //他是第几条的
                    count++;

                    long pageNo = count % Const.PAGE_SIZE == 0 ? count / Const.PAGE_SIZE : count / Const.PAGE_SIZE + 1;
                    StringBuilder sb = new StringBuilder();
                    sb.append("::");
                    sb.append(objectMapper.writeValueAsString(pageNo));
//                    sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
                    String pagePrefix = Const.HOT_BLOGS + "::BlogController::list" + sb;
                    redisTemplate.delete(pagePrefix);

                    /*
                     * 查出年份分页这条博客在第几页
                     */
                    int countYear = blogMapper.getPageYearCount(blog.getCreated().toString(), year);
                    countYear++;

                    int pageYearNo = countYear % Const.PAGE_SIZE == 0 ? countYear / Const.PAGE_SIZE : countYear / Const.PAGE_SIZE + 1;
                    StringBuilder s = new StringBuilder();
                    s.append("::");
                    s.append(objectMapper.writeValueAsString(pageYearNo));
                    s.append("::");
                    s.append(objectMapper.writeValueAsString(year));
//                    s = new StringBuilder(Arrays.toString(DigestUtil.md5(s.toString())));
                    String pageYearPrefix = Const.HOT_BLOGS + "::BlogController::listByYear" + s;
                    redisTemplate.delete(pageYearPrefix);

                    log.info("更新博客Redis删除完毕");

                    /*
                     * ES流程
                     */

                    BlogPostDocument postDocument = MyUtils.blogToDocument(blogExisted);

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

                    /*
                     * Redis流程
                     */

                    StringBuilder builder = new StringBuilder();
                    builder.append("::");
                    builder.append(objectMapper.writeValueAsString(deleteId));
//                    builder = new StringBuilder(Arrays.toString(DigestUtil.md5(builder.toString())));

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
                        redisTemplate.delete(keys);
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
                break;
            case PostMQIndexMessage.CREATE:

                String createUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");
                if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + createUUID))) {
                    Long createId = message.getPostId();
                    BlogEntity newBlog = blogService.getById(createId);

                    /*
                    、Redis流程
                     */
                    StringBuilder builder = new StringBuilder();
                    builder.append("::");
                    builder.append(objectMapper.writeValueAsString(createId));
//                    builder = new StringBuilder(Arrays.toString(DigestUtil.md5(builder.toString())));

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
                        redisTemplate.delete(keys);
                    }

                    //年份过滤bloom更新
                    int year = newBlog.getCreated().getYear();
                    redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_YEARS, year, true);

                    log.info("新增博客Redis删除完毕");


                    /*
                    ES流程
                     */

                    BlogPostDocument newDocument = MyUtils.blogToDocument(newBlog);
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
