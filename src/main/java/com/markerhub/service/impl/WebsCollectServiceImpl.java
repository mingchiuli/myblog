package com.markerhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.User;
import com.markerhub.search.model.CollectWebsiteDocument;
import com.markerhub.service.UserService;
import com.markerhub.service.WebsCollectService;
import com.markerhub.util.JwtUtils;
import com.markerhub.util.MyUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * @author mingchiuli
 * @create 2022-04-20 11:02 AM
 */
@Service
@Slf4j
public class WebsCollectServiceImpl implements WebsCollectService {

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
        Long id = userService.getOne(new QueryWrapper<User>().eq("username", "tokentooler")).getId();

        return jwtUtils.generateToken(id);
    }

    @Override
    public void addWebsite(CollectWebsiteDocument document) {

        document.setCreated(LocalDateTime.now().minusHours(Const.GMT_PLUS_8));

        CollectWebsiteDocument res =  elasticsearchRestTemplate.save(document);

        log.info("新增网页搜藏结果:{}", res);
    }

    @SneakyThrows
    @Override
    public void modifyWebsite(CollectWebsiteDocument document) {
        document.setCreated(document.getCreated().minusHours(Const.GMT_PLUS_8));

        ObjectMapper objectMapper = new ObjectMapper();
        String obj = objectMapper.writeValueAsString(document);
        Document doc = Document.parse(obj);

        UpdateQuery query = UpdateQuery
                .builder(document.getId())
                .withDocument(doc)
                .build();

        IndexCoordinates indexCoordinates = elasticsearchRestTemplate.getIndexCoordinatesFor(CollectWebsiteDocument.class);

        UpdateResponse update = elasticsearchRestTemplate.update(query, indexCoordinates);

        String result = String.valueOf(update.getResult());

        log.info("修改网页搜藏结果:{}", result);
    }

    @Override
    public Page<CollectWebsiteDocument> searchWebsiteAuth(Integer currentPage, String keyword) {
        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description");

        NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(multiMatchQueryBuilder))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .build();

        long count = elasticsearchRestTemplate.count(searchQueryCount, CollectWebsiteDocument.class);

        NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(multiMatchQueryBuilder))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .withPageable(PageRequest.of(currentPage - 1, Const.WEB_SIZE))
                .build();

        SearchHits<CollectWebsiteDocument> search = elasticsearchRestTemplate.search(searchQueryHits, CollectWebsiteDocument.class);

        Page<CollectWebsiteDocument> page = MyUtils.hitsToPage(search, currentPage, Const.WEB_SIZE, count);

        for (CollectWebsiteDocument record : page.getRecords()) {
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }

        log.info("{} 关键词被链接搜索", keyword);

        return page;
    }

    @Override
    public Page<CollectWebsiteDocument> searchRecent(Integer currentPage) {
        NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.matchQuery("status", 0))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                //页码从0开始
                .build();

        long count = elasticsearchRestTemplate.count(searchQueryCount, CollectWebsiteDocument.class);

        NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.matchQuery("status", 0))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .withPageable(PageRequest.of(currentPage - 1, Const.WEB_SIZE))
                //页码从0开始
                .build();

        SearchHits<CollectWebsiteDocument> search = elasticsearchRestTemplate.search(searchQueryHits, CollectWebsiteDocument.class);

        Page<CollectWebsiteDocument> page = MyUtils.hitsToPage(search, currentPage, Const.WEB_SIZE, count);

        for (CollectWebsiteDocument record : page.getRecords()) {
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }
        return page;
    }

    @Override
    public Page<CollectWebsiteDocument> searchWebsite(Integer currentPage, String keyword) {
        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description");

        NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(multiMatchQueryBuilder)
                        .must(QueryBuilders.termQuery("status", 0)))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .build();

        long count = elasticsearchRestTemplate.count(searchQueryCount, CollectWebsiteDocument.class);

        NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(multiMatchQueryBuilder)
                        .must(QueryBuilders.termQuery("status", 0)))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .withPageable(PageRequest.of(currentPage - 1, Const.WEB_SIZE))
                .build();

        SearchHits<CollectWebsiteDocument> search = elasticsearchRestTemplate.search(searchQueryHits, CollectWebsiteDocument.class);

        Page<CollectWebsiteDocument> page = MyUtils.hitsToPage(search, currentPage, Const.WEB_SIZE, count);

        for (CollectWebsiteDocument record : page.getRecords()) {
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }

        log.info("{} 关键词被链接搜索", keyword);

        return page;
    }


}
