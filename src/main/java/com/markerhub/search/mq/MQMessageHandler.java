package com.markerhub.search.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.config.RabbitConfig;
import com.markerhub.entity.Blog;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.service.BlogService;
import com.markerhub.util.MyUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;
import org.springframework.stereotype.Component;

/**
 * @author mingchiuli
 * @create 2021-12-13 11:38 AM
 */
@Slf4j
@Component
@RabbitListener(queues = RabbitConfig.ES_QUEUE)
public class MQMessageHandler {

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
    public void handler(PostMQIndexMessage message) {
        switch (message.getType()) {
            case PostMQIndexMessage.UPDATE:

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

                break;
            case PostMQIndexMessage.REMOVE:

                Long deleteId = message.getPostId();

                String delete = elasticsearchRestTemplate.delete(deleteId.toString(), BlogPostDocument.class);

                log.info("ES删除{}号结果: {}", deleteId, delete);

                break;
            case PostMQIndexMessage.CREATE:

                Long createId = message.getPostId();

                Blog newBlog = blogService.getById(createId);

                BlogPostDocument newDocument = MyUtil.blogToDocument(newBlog);

                BlogPostDocument save = elasticsearchRestTemplate.save(newDocument);

                log.info("ES创建{}号结果: {}", createId, save);

                break;
            default:
                log.error("ES没找到对应的消息类型: {}", message);
        }
    }
}
