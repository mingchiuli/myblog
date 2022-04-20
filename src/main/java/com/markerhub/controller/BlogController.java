package com.markerhub.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.cache.Cache;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Blog;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.service.BlogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前台博客操作
 * </p>
 *
 * @since 2020-05-25
 */
@Slf4j
@RestController
public class BlogController {

    BlogService blogService;

    @Autowired
    private void setBlogServiceImpl(BlogService blogService) {
        this.blogService = blogService;
    }


    /**
     * 按照年份给出博客内容
     * @param currentPage
     * @param year
     * @return
     */

    @GetMapping("/blogsByYear/{year}/{currentPage}")
    @Cache(name = Const.HOT_BLOGS)//缓存页面信息一分钟
    public Result listByYear(@PathVariable(name = "currentPage") Integer currentPage, @PathVariable(name = "year") Integer year) {
        IPage<Blog> pageData = blogService.listByYear(currentPage, year);
        return Result.succ(pageData);
    }

    @GetMapping("/getCountByYear/{year}")
    public Result getCountByYear(@PathVariable(name = "year") Integer year) {
        Integer count = blogService.getYearCount(year);
        return Result.succ(count);
    }

    /**
     * 所有博客内容
     * @param currentPage
     * @return
     */
    @Cache(name = Const.HOT_BLOGS)//缓存页面信息一分钟
    @GetMapping("/blogs/{currentPage}")
    public Result list(@PathVariable(name = "currentPage") Integer currentPage) {

        IPage<Blog> pageData = blogService.listBlogsByPage(currentPage);
        return Result.succ(pageData);
    }


    /**
     * 博客详情
     * @param id
     * @return
     */

    @GetMapping("/blog/{id}")
    public Result detail(@PathVariable(name = "id") Long id) {
        Blog blog = blogService.getBlogDetail(id);
        return Result.succ(blog);
    }

    @GetMapping("/blogAuthorized/{id}")
    @RequiresRoles(Const.ADMIN)
    public Result detailAuthorized(@PathVariable(name = "id") Long id) {
        Blog blog = blogService.getAuthorizedBlogDetail(id);
        return Result.succ(blog);
    }

    /**
     * 查看加锁文章
     * @param blogId
     * @param token
     * @return
     */
    @GetMapping("blogToken/{blogId}/{token}")
    public Result blogToken(@PathVariable Long blogId, @PathVariable String token) {
        Blog blog = blogService.getLockedBlog(blogId, token);
        if (blog == null) {
            return Result.fail("密钥错误");
        } else {
            return Result.succ(blog);
        }
    }


    /**
     * 搜索功能，从es搜索
     */
    @GetMapping("/search/{currentPage}")
    public Result search(@PathVariable Integer currentPage, @RequestParam String keyword) {
        Page<BlogPostDocument> page = blogService.selectBlogsByES(currentPage, keyword);
        return Result.succ(page);
    }


    @GetMapping("/searchByYear/{currentPage}/{year}")
    public Result searchByYear(@PathVariable Integer currentPage, @RequestParam String keyword, @PathVariable Integer year) {
        Page<BlogPostDocument> page = blogService.selectYearBlogsByES(currentPage, keyword, year);
        return Result.succ(page);
    }

    /**
     * 修改博客
     * @param blog
     * @return
     */
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    @PostMapping("/blog/edit")
    public Result edit(@Validated @RequestBody Blog blog) {
        blogService.updateBlog(blog);
        return Result.succ(null);
    }

    /**
     * 初始化文章，目的是拿到创建时间，从而让每篇文章的上传图片位于每一个文件夹中
     * @return
     */
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    @GetMapping("/addNewBlog")
    public Result addNewBlog() {
        Long id = blogService.initBlog();
        return Result.succ(id);
    }


    /**
     * 查看已经删除的博客
     * @param currentPage
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @Cache(name = Const.HOT_DELETED)
    @GetMapping("/queryDeletedBlogs")
    public Result listDeleted(@RequestParam String title, @RequestParam Integer currentPage, @RequestParam Integer size, @RequestParam Long userId) {
        Page<Blog> page = blogService.selectDeletedBlogs(title, currentPage, size, userId);
        return Result.succ(page);

    }

    /**
     * 恢复删除的博客
     * @param id
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/recoverBlogs/{id}/{userId}")
    public Result recoverBlog(@PathVariable(name = "id") Long id, @PathVariable(name = "userId") Long userId) {
        blogService.recoverBlog(id, userId);
        return Result.succ(null);
    }

    /**
     * 更改文章状态，0为公开，1为登录后可阅读
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/modifyBlogStatus/{id}/{status}")
    public Result modifyBlogStatus(@PathVariable Long id, @PathVariable Integer status) {
        blogService.changeBlogStatus(id, status);
        return Result.succ(null);
    }

    @RequiresRoles(value = {Const.ADMIN, Const.BOY, Const.GIRL, Const.GUEST}, logical = Logical.OR)
    @GetMapping("/getAllBlogs")
    @Cache(name = Const.HOT_BLOGS)//缓存页面信息一分钟
    public Result getAllBlogs(@RequestParam Integer currentPage, @RequestParam Integer size) {
        Page<Blog> page = blogService.getAllBlogs(currentPage, size);
        return Result.succ(page);

    }


    /**
     * 查询博客，包含总阅读数和最近7日阅读数
     * @param currentPage
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @Cache(name = Const.HOT_BLOGS)
    @GetMapping("/queryBlogs")
    public Result queryBlogs(@RequestParam String keyword, @RequestParam Integer currentPage, @RequestParam Integer size) {
        Page<Blog> page = blogService.queryBlogsAbstract(keyword, currentPage, size);
        return Result.succ(page);
    }


    /**
     * 删除博客
     * @param ids
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/deleteBlogs")
    public Result deleteBlogs(@RequestBody Long[] ids) {
        blogService.deleteBlogs(ids);
        return Result.succ("删除成功");
    }


    /**
     * 设置阅读密钥
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/setBlogToken")
    public Result setBlogToken() {
        blogService.setBlogToken();
        return Result.succ(null);
    }

    /**
     * 获取阅读密钥
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/getBlogToken")
    public Result getBlogToken() {
        String token = blogService.getBlogToken();
        return Result.succ(token);
    }

    /**
     * 获取文章状态
     */
    @GetMapping("/blogStatus/{blogId}")
    public Result getBlogStatus(@PathVariable Long blogId) {
        Integer status = blogService.getOne(new QueryWrapper<Blog>().eq("id", blogId).select("status")).getStatus();
        return Result.succ(status);
    }

}
