package com.markerhub.search.mq.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.BlogEntity;
import com.markerhub.mapper.BlogMapper;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.BlogIndexEnum;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.search.mq.handler.BlogIndexHandler;
import com.markerhub.service.BlogService;
import com.markerhub.utils.MyUtils;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Slf4j
public class UpdateBlogIndexHandler implements BlogIndexHandler {

    BlogMapper blogMapper;


    ObjectMapper objectMapper;


    RedisTemplate<String, Object> redisTemplate;

    BlogService blogService;


    ElasticsearchRestTemplate elasticsearchRestTemplate;

    public UpdateBlogIndexHandler(BlogMapper blogMapper, ObjectMapper objectMapper, RedisTemplate<String, Object> redisTemplate, BlogService blogService, ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.blogMapper = blogMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.blogService = blogService;
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }

    @Override
    public boolean supports(BlogIndexEnum blogIndexEnum) {
        return BlogIndexEnum.UPDATE.equals(blogIndexEnum);
    }

    @Override
    @SneakyThrows
    public void handle(PostMQIndexMessage message, Channel channel, Message msg) {
        String updateUUID = msg.getMessageProperties().getHeader("spring_returned_message_correlation");

        if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.CONSUME_MONITOR + updateUUID))) {
            Long updateId = message.getPostId();
            BlogEntity blog = blogService.getById(updateId);

                    /*
                     Redis流程
                     */
            //删除博客的缓存（不用lua脚本也行，delete返回值是一个Long，加锁博客缓存中不存在也可以删）
            String builder = "::" + updateId;
            String contentPrefix = Const.HOT_BLOG + "::BlogServiceImpl::getBlogDetail" + builder;
            redisTemplate.delete(contentPrefix);

            //删除博客所在页的缓存
            //年份
            int year = blog.getCreated().getYear();
            /*
             * 查出普通分页这条博客在第几页
             */
            long count = blogMapper.getPageCount(blog.getCreated().toString());
            //他是第几条的
            count++;

            long pageNo = count % Const.PAGE_SIZE == 0 ? count / Const.PAGE_SIZE : count / Const.PAGE_SIZE + 1;
            String sb = "::" + pageNo;
            String pagePrefix = Const.HOT_BLOGS + "::BlogController::list" + sb;
            redisTemplate.delete(pagePrefix);

            /*
             * 查出年份分页这条博客在第几页
             */
            int countYear = blogMapper.getPageYearCount(blog.getCreated().toString(), year);
            countYear++;

            int pageYearNo = countYear % Const.PAGE_SIZE == 0 ? countYear / Const.PAGE_SIZE : countYear / Const.PAGE_SIZE + 1;
            String s = "::" + pageYearNo + "::" + year;
            String pageYearPrefix = Const.HOT_BLOGS + "::BlogController::listByYear" + s;
            redisTemplate.delete(pageYearPrefix);

            log.info("更新博客Redis删除完毕");

            /*
             * ES流程
             */

            BlogPostDocument postDocument = MyUtils.blogToDocument(blog);

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
    }
}
