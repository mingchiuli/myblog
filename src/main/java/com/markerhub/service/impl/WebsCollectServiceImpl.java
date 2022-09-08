package com.markerhub.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.common.vo.WebsCollectDocumentVo;
import com.markerhub.search.model.WebsCollectDocument;
import com.markerhub.service.UserService;
import com.markerhub.service.WebsCollectService;
import com.markerhub.utils.JwtUtils;
import com.markerhub.utils.MyUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author mingchiuli
 * @create 2022-04-20 11:02 AM
 */
@Service
@Slf4j
public class WebsCollectServiceImpl implements WebsCollectService {

    ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ThreadPoolExecutor executor;

    @Autowired
    @Qualifier("pageThreadPoolExecutor")
    public void setExecutor(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    JwtUtils jwtUtils;

    @Autowired
    public void setJwtUtils(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    public void setElasticsearchRestTemplate(ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }


    @Override
    public String getJWT() {
        return jwtUtils.generateToken("tokentooler");
    }

    @Override
    public void addWebsite(WebsCollectDocument document) {

        document.setCreated(LocalDateTime.now().minusHours(Const.GMT_PLUS_8));

        WebsCollectDocument res =  elasticsearchRestTemplate.save(document);

        log.info("新增网页搜藏结果:{}", res);
    }

    @SneakyThrows
    @Override
    public void modifyWebsite(WebsCollectDocument document) {
        document.setCreated(document.getCreated().minusHours(Const.GMT_PLUS_8));

        String obj = objectMapper.writeValueAsString(document);
        Document doc = Document.parse(obj);

        UpdateQuery query = UpdateQuery
                .builder(document.getId())
                .withDocument(doc)
                .build();

        IndexCoordinates indexCoordinates = elasticsearchRestTemplate.getIndexCoordinatesFor(WebsCollectDocument.class);

        UpdateResponse update = elasticsearchRestTemplate.update(query, indexCoordinates);

        String result = String.valueOf(update.getResult());

        log.info("修改网页搜藏结果:{}", result);
    }

    @SneakyThrows
    @Override
    public Page<WebsCollectDocumentVo> searchWebsiteAuth(Integer currentPage, String keyword) {
        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description");

        CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .must(multiMatchQueryBuilder))
                    .build();

            return elasticsearchRestTemplate.count(searchQueryCount, WebsCollectDocument.class);
        }, executor);


        CompletableFuture<SearchHits<WebsCollectDocument>> searchHitsFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .must(multiMatchQueryBuilder))
                    .withSorts(SortBuilders.scoreSort())
                    .withPageable(PageRequest.of(currentPage - 1, Const.WEB_SIZE))
                    .withHighlightBuilder(new HighlightBuilder()
                            .field("title").field("description").preTags("<b style='color:red'>").postTags("</b>"))
                    .build();

            return elasticsearchRestTemplate.search(searchQueryHits, WebsCollectDocument.class);
        }, executor);

        CompletableFuture.allOf(countFuture, searchHitsFuture).get();

        long count = countFuture.get();
        SearchHits<WebsCollectDocument> search = searchHitsFuture.get();

        Page<WebsCollectDocumentVo> page = MyUtils.hitsToPage(search, WebsCollectDocumentVo.class, currentPage, Const.WEB_SIZE, count);

        page.getRecords().forEach(record -> record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8)));

        return page;
    }

    @SneakyThrows
    @Override
    public Page<WebsCollectDocument> searchRecent(Integer currentPage) {

        CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.termQuery("status", 0))
                    //页码从0开始
                    .build();

            return elasticsearchRestTemplate.count(searchQueryCount, WebsCollectDocument.class);
        }, executor);

        CompletableFuture<SearchHits<WebsCollectDocument>> searchHitsFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.termQuery("status", 0))
                    .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                    .withPageable(PageRequest.of(currentPage - 1, Const.WEB_SIZE))
                    //页码从0开始
                    .build();

            return elasticsearchRestTemplate.search(searchQueryHits, WebsCollectDocument.class);
        }, executor);

        CompletableFuture.allOf(countFuture, searchHitsFuture).get();

        long count = countFuture.get();
        SearchHits<WebsCollectDocument> search = searchHitsFuture.get();

        Page<WebsCollectDocument> page = MyUtils.hitsToPage(search, currentPage, Const.WEB_SIZE, count);

        page.getRecords().forEach(record -> record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8)));
        return page;
    }

    @SneakyThrows
    @Override
    public Page<WebsCollectDocumentVo> searchWebsite(Integer currentPage, String keyword) {
        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description");

        CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(() -> {

            NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .must(multiMatchQueryBuilder)
                            .filter(QueryBuilders.termQuery("status", 0)))
                    .build();

            return elasticsearchRestTemplate.count(searchQueryCount, WebsCollectDocument.class);
        }, executor);

        CompletableFuture<SearchHits<WebsCollectDocument>> searchHitsFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .must(multiMatchQueryBuilder)
                            .filter(QueryBuilders.termQuery("status", 0)))
                    .withSorts(SortBuilders.scoreSort())
                    .withPageable(PageRequest.of(currentPage - 1, Const.WEB_SIZE))
                    .withHighlightBuilder(new HighlightBuilder()
                            .field("title").field("description").preTags("<b style='color:red'>").postTags("</b>"))
                    .build();

            return elasticsearchRestTemplate.search(searchQueryHits, WebsCollectDocument.class);
        }, executor);

        CompletableFuture.allOf(countFuture, searchHitsFuture).get();

        long count = countFuture.get();
        SearchHits<WebsCollectDocument> search = searchHitsFuture.get();


        Page<WebsCollectDocumentVo> page = MyUtils.hitsToPage(search, WebsCollectDocumentVo.class, currentPage, Const.WEB_SIZE, count);

        page.getRecords().forEach(record -> record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8)));
        return page;
    }


}
