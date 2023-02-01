package com.markerhub.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.cache.Cache;
import com.markerhub.common.lang.Result;
import com.markerhub.common.vo.WebsitePostDocumentVo;
import com.markerhub.search.model.WebsitePostDocument;
import com.markerhub.service.UserService;
import com.markerhub.service.WebsCollectService;
import com.markerhub.utils.JwtUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * 收藏搜索，增删改查
 * @author mingchiuli
 * @create 2022-01-29 3:08 PM
 */
@RestController
@Slf4j
public class WebsCollectController {

    UserService userService;


    JwtUtils jwtUtils;

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    WebsCollectService websCollectService;

    public WebsCollectController(UserService userService, JwtUtils jwtUtils, ElasticsearchRestTemplate elasticsearchRestTemplate, WebsCollectService websCollectService) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
        this.websCollectService = websCollectService;
    }

    @GetMapping("/getJWT")
    @Cache(name = "jwt")
    public Result getJWT() {
        String jwt = websCollectService.getJWT();
        return Result.succ(jwt);
    }

    @PostMapping("/addWebsite")
    public Result addWebsite(@Validated @RequestBody WebsitePostDocument document) {
        websCollectService.addWebsite(document);
        return Result.succ(null);
    }

    @GetMapping("/getWebInfo/{id}")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    public Result getWebInfo(@PathVariable String id) {
        WebsitePostDocument document = elasticsearchRestTemplate.get(id, WebsitePostDocument.class);

        if (document != null) {
            return Result.succ(document);
        } else {
            return Result.fail("查询失败");
        }

    }

    @SneakyThrows
    @PostMapping("/modifyWebsite")
    @PreAuthorize("hasRole('admin')")
    public Result modifyWebsite(@Validated @RequestBody WebsitePostDocument document) {
        websCollectService.modifyWebsite(document);
        return Result.succ(null);
    }

    @GetMapping("/deleteWebsite/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result deleteWebsite(@PathVariable String id) {
        String delete = elasticsearchRestTemplate.delete(id, WebsitePostDocument.class);
        log.info("删除网页搜藏结果:{}", delete);
        return Result.succ(null);
    }

    @GetMapping("searchWebsiteAuth/{currentPage}")
    @PreAuthorize("hasRole('admin')")
    public Result searchWebsiteAuth(@PathVariable Integer currentPage, @RequestParam String keyword) {
        Page<WebsitePostDocumentVo> page = websCollectService.searchWebsiteAuth(currentPage, keyword);
        return Result.succ(page);
    }

    @GetMapping("searchRecent/{currentPage}")
    public Result searchRecent(@PathVariable Integer currentPage) {
        Page<WebsitePostDocument> page = websCollectService.searchRecent(currentPage);
        return Result.succ(page);
    }


    @GetMapping("searchWebsite/{currentPage}")
    public Result searchWebsite(@PathVariable Integer currentPage, @RequestParam String keyword) {
        Page<WebsitePostDocumentVo> page = websCollectService.searchWebsite(currentPage, keyword);
        return Result.succ(page);
    }

}
