package com.markerhub.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.User;
import com.markerhub.search.model.CollectWebsiteDocument;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtils;
import com.markerhub.util.MyUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 收藏搜索，增删改查
 * @author mingchiuli
 * @create 2022-01-29 3:08 PM
 */
@RestController
@Slf4j
public class WebsController {

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


    @GetMapping("/getJWT")
    public Result getJWT() {

        Long id = userService.getOne(new QueryWrapper<User>().eq("username", "tokentooler")).getId();

        String jwt = jwtUtils.generateToken(id);

        return Result.succ(jwt);

    }

    @PostMapping("/addWebsite")
    @RequiresAuthentication
    public Result addWebsite(@Validated @RequestBody CollectWebsiteDocument document) {

        document.setCreated(LocalDateTime.now().minusHours(Const.GMT_PLUS_8));

        CollectWebsiteDocument res =  elasticsearchRestTemplate.save(document);

        log.info("新增网页搜藏结果:{}", res);

        return Result.succ(null);
    }

    @GetMapping("/getWebInfo/{id}")
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    public Result getWebInfo(@PathVariable String id) {
        CollectWebsiteDocument document = elasticsearchRestTemplate.get(id, CollectWebsiteDocument.class);

        if (document != null) {
            document.setCreated(document.getCreated().plusHours(Const.GMT_PLUS_8));
            return Result.succ(document);
        } else {
            return Result.fail("查询失败");
        }

    }


    @SneakyThrows
    @PostMapping("/modifyWebsite")
    @RequiresRoles(Const.ADMIN)
    public Result modifyWebsite(@Validated @RequestBody CollectWebsiteDocument document) {

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

        return Result.succ(null);
    }

    @GetMapping("/deleteWebsite/{id}")
    @RequiresRoles(Const.ADMIN)
    public Result deleteWebsite(@PathVariable String id) {
        String delete = elasticsearchRestTemplate.delete(id, CollectWebsiteDocument.class);

        log.info("删除网页搜藏结果:{}", delete);

        return Result.succ(null);
    }

    @GetMapping("searchWebsiteAuth/{currentPage}")
    @RequiresRoles(Const.ADMIN)
    public Result searchWebsiteAuth(@PathVariable Integer currentPage, @RequestParam String keyword) {
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

        return Result.succ(page);
    }

    @GetMapping("searchRecent/{currentPage}")
    public Result searchRecent(@PathVariable Integer currentPage) {

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

        return Result.succ(page);

    }


    @GetMapping("searchWebsite/{currentPage}")
    public Result searchWebsite(@PathVariable Integer currentPage, @RequestParam String keyword) {
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

        return Result.succ(page);
    }


}
