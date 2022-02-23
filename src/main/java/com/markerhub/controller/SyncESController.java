package com.markerhub.controller;

import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Blog;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.service.BlogService;
import com.markerhub.util.MyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用于将原来的日志同步到es中，要求最高权限才能执行
 * 宜用postman发请求执行，前端不做按钮
 * @author mingchiuli
 * @create 2021-12-14 12:14 AM
 */
@Slf4j
@RestController
public class SyncESController {

    BlogService blogService;

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    @Autowired
    public void setElasticsearchRestTemplate(ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }

    @GetMapping("/sync")
    @RequiresRoles(Const.ADMIN)
    public Result sync() {
        log.info("开始同步ES了");

        List<Blog> blogs = blogService.list();
        for (Blog blog : blogs) {
            BlogPostDocument document = MyUtils.blogToDocument(blog);
            elasticsearchRestTemplate.save(document);
        }

        log.info("ES同步完成了");

        return Result.succ(null);
    }
}
