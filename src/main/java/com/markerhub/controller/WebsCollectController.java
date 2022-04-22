package com.markerhub.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.common.vo.WebsCollectDocumentVo;
import com.markerhub.search.model.WebsCollectDocument;
import com.markerhub.service.UserService;
import com.markerhub.service.WebsCollectService;
import com.markerhub.util.JwtUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
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

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    JwtUtil jwtUtil;

    @Autowired
    public void setJwtUtils(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    public void setElasticsearchRestTemplate(ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }

    WebsCollectService websCollectService;

    @Autowired
    public void setWebsCollectService(WebsCollectService websCollectService) {
        this.websCollectService = websCollectService;
    }

    @GetMapping("/getJWT")
    public Result getJWT() {
        String jwt = websCollectService.getJWT();
        return Result.succ(jwt);
    }

    @PostMapping("/addWebsite")
    @RequiresAuthentication
    public Result addWebsite(@Validated @RequestBody WebsCollectDocument document) {
        websCollectService.addWebsite(document);
        return Result.succ(null);
    }

    @GetMapping("/getWebInfo/{id}")
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    public Result getWebInfo(@PathVariable String id) {
        WebsCollectDocument document = elasticsearchRestTemplate.get(id, WebsCollectDocument.class);

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
    public Result modifyWebsite(@Validated @RequestBody WebsCollectDocument document) {
        websCollectService.modifyWebsite(document);
        return Result.succ(null);
    }

    @GetMapping("/deleteWebsite/{id}")
    @RequiresRoles(Const.ADMIN)
    public Result deleteWebsite(@PathVariable String id) {
        String delete = elasticsearchRestTemplate.delete(id, WebsCollectDocument.class);
        log.info("删除网页搜藏结果:{}", delete);
        return Result.succ(null);
    }

    @GetMapping("searchWebsiteAuth/{currentPage}")
    @RequiresRoles(Const.ADMIN)
    public Result searchWebsiteAuth(@PathVariable Integer currentPage, @RequestParam String keyword) {
        Page<WebsCollectDocumentVo> page = websCollectService.searchWebsiteAuth(currentPage, keyword);
        return Result.succ(page);
    }

    @GetMapping("searchRecent/{currentPage}")
    public Result searchRecent(@PathVariable Integer currentPage) {
        Page<WebsCollectDocument> page = websCollectService.searchRecent(currentPage);
        return Result.succ(page);
    }


    @GetMapping("searchWebsite/{currentPage}")
    public Result searchWebsite(@PathVariable Integer currentPage, @RequestParam String keyword) {
        Page<WebsCollectDocumentVo> page = websCollectService.searchWebsite(currentPage, keyword);
        return Result.succ(page);
    }

}
