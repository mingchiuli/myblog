package com.markerhub.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.cache.Cache;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Blog;
import com.markerhub.entity.User;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.service.BlogService;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtils;
import com.markerhub.util.MyUtils;
import com.markerhub.util.ShiroUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

    @Value("${uploadPath}")
    private String baseFolderPath;

    @Value("${imgFoldName}")
    private String img;

    JwtUtils jwtUtils;

    @Autowired
    public void setJwtUtils(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    BlogService blogService;

    @Autowired
    private void setBlogServiceImpl(BlogService blogService) {
        this.blogService = blogService;
    }


    UserService userService;

    @Autowired
    private void setUserServiceImpl(UserService userService) {
        this.userService = userService;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private void setRedisTemplateImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    public void setElasticsearchRestTemplate(ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }

    AmqpTemplate amqpTemplate;

    @Autowired
    public void setAmqpTemplate(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
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

        Page<Blog> page = new Page<>(currentPage, Const.PAGE_SIZE);
        QueryWrapper<Blog> queryWrapper = new QueryWrapper<>();
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);

        QueryWrapper<Blog> wrapper = queryWrapper.select("id", "title", "description", "link", "created").between("created", start, end).orderByAsc("created");
        IPage<Blog> pageData = blogService.page(page, wrapper);

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

        int totalPage = blogService.count() % Const.PAGE_SIZE == 0 ? blogService.count() / Const.PAGE_SIZE : blogService.count() / Const.PAGE_SIZE + 1;

        if (currentPage > totalPage) {
            Page<Blog> page = new Page<>(totalPage, Const.PAGE_SIZE);
            IPage<Blog> pageData = blogService.page(page, new QueryWrapper<Blog>().select("title", "description", "link", "created").orderByDesc("created"));
            return Result.succ(pageData);
        }

        if (totalPage < 1) {
            Page<Blog> page = new Page<>(1, Const.PAGE_SIZE);
            IPage<Blog> pageData = blogService.page(page, new QueryWrapper<Blog>().select("title", "description", "link", "created").orderByDesc("created"));
            return Result.succ(pageData);
        }

        Page<Blog> page = new Page<>(currentPage, Const.PAGE_SIZE);
        IPage<Blog> pageData = blogService.page(page, new QueryWrapper<Blog>().select("id", "title", "description", "link", "created").orderByDesc("created"));

        return Result.succ(pageData);
    }


    /**
     * 博客详情
     * @param id
     * @return
     */

    @GetMapping("/blog/{id}")
    public Result detail(@PathVariable(name = "id") Long id) {

        if (blogService.getById(id).getStatus() == 1) {
            throw new AuthenticationException("没有访问权限");
        }

        Blog blog = blogService.getBaseMapper().selectOne(new QueryWrapper<Blog>().eq("status", 0).eq("id", id));

        Assert.notNull(blog, "该博客不存在");

        MyUtils.setReadCount(id);

        return Result.succ(blog);
    }

    @GetMapping("/blogAuthorized/{id}")
    @RequiresRoles(Const.ADMIN)
    public Result detailAuthorized(@PathVariable(name = "id") Long id) {

        Blog blog = blogService.getById(id);

        Assert.notNull(blog, "该博客不存在");

        MyUtils.setReadCount(id);

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
        token = token.trim();
        String exist = (String) redisTemplate.opsForValue().get(Const.READ_TOKEN);
        if (StringUtils.hasLength(token) && StringUtils.hasLength(exist)) {
            if (!token.equals(exist)) {
                return Result.fail("密钥错误");
            } else {
                Blog blog = blogService.getById(blogId);
                return Result.succ(blog);
            }
        } else {
            return Result.fail("该博客暂时无法查看");
        }
    }


    /**
     * 搜索功能，从es搜索
     */
    @GetMapping("/search/{currentPage}")
    public Result search(@PathVariable Integer currentPage, @RequestParam String keyword) {

        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description", "link", "content");

        NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("status", 0))
                        .must(multiMatchQueryBuilder))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .build();

        long count = elasticsearchRestTemplate.count(searchQueryCount, BlogPostDocument.class);

        NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("status", 0))
                        .must(multiMatchQueryBuilder))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .withPageable(PageRequest.of(currentPage - 1, Const.PAGE_SIZE))
                .build();

        SearchHits<BlogPostDocument> search = elasticsearchRestTemplate.search(searchQueryHits, BlogPostDocument.class);

        Page<BlogPostDocument> page = MyUtils.hitsToPage(search, currentPage, Const.PAGE_SIZE, count);

        for (BlogPostDocument record : page.getRecords()) {
            record.setContent(null);
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }


        log.info("{} 关键词被首页搜索", keyword);

        return Result.succ(page);
    }


    @GetMapping("/searchByYear/{currentPage}/{year}")
    public Result searchByYear(@PathVariable Integer currentPage, @RequestParam String keyword, @PathVariable Integer year) {

        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description", "link", "content");

        NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(QueryBuilders.rangeQuery("created").gte(year + "-01-01T00:00:00").lte(year + "-12-31T23:59:59")
                                .includeUpper(true)
                                .includeLower(true))
                        .must(multiMatchQueryBuilder)
                        .must(QueryBuilders.termQuery("status", 0)))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .build();

        long count = elasticsearchRestTemplate.count(searchQueryCount, BlogPostDocument.class);

        NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.boolQuery()
                        .must(QueryBuilders.rangeQuery("created").gte(year + "-01-01T00:00:00").lte(year + "-12-31T23:59:59")
                                .includeUpper(true)
                                .includeLower(true))
                        .must(multiMatchQueryBuilder)
                        .must(QueryBuilders.termQuery("status", 0)))
                .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                .withPageable(PageRequest.of(currentPage - 1, Const.PAGE_SIZE))
                .build();

        SearchHits<BlogPostDocument> search = elasticsearchRestTemplate.search(searchQueryHits, BlogPostDocument.class);

        Page<BlogPostDocument> page = MyUtils.hitsToPage(search, currentPage, Const.PAGE_SIZE, count);

        for (BlogPostDocument record : page.getRecords()) {
            record.setContent(null);
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }

        log.info("{} 关键词被{}年界面搜索", keyword, year);

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
        //都是更新，之前初始化过了

        Blog temp = blogService.getById(blog.getId());
        // 只能编辑自己的文章
        Assert.isTrue(temp.getUserId().longValue() == ShiroUtil.getProfile().getId().longValue(), "没有权限编辑");

        BeanUtil.copyProperties(blog, temp, "id", "userId", "created", "status");
        boolean update = blogService.saveOrUpdate(temp);

        log.info("数据库更新{}号博客结果:{}", blog.getId(), update);

        Assert.isTrue(update, "出现错误");

        //通知消息给mq,更新
        MyUtils.sendBlogMessageToMQ(blog.getId(), PostMQIndexMessage.UPDATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_BLOG_PREFIX);

        return Result.succ(null);
    }

    /**
     * 初始化文章，目的是拿到创建时间，从而让每篇文章的上传图片位于每一个文件夹中
     * @return
     */
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    @GetMapping("/addNewBlog")
    public Result addNewBlog() {
        Blog blog = new Blog();

        MyUtils.initBlog(blog);

        boolean add = blogService.saveOrUpdate(blog);

        log.info("初始化日志结果:{}", add);

        //通知消息给mq
        MyUtils.sendBlogMessageToMQ(blog.getId(), PostMQIndexMessage.CREATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_BLOG_PREFIX);
        return Result.succ(blog.getId());
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

        User one = userService.getOne(new QueryWrapper<User>().eq("id", userId).last("LIMIT 1"));
        String username = one.getUsername();

        String prefix = userId + Const.QUERY_ALL_DELETED;
        Set<String> keys = redisTemplate.keys(prefix);

        if (keys != null) {
            List<Object> rawAllDeleted = redisTemplate.opsForValue().multiGet(keys);
            if (rawAllDeleted != null) {
                ArrayList<Blog> allDeleted = new ArrayList<>();
                for (Object value : rawAllDeleted) {
                    Blog blog = MyUtils.jsonToObj(value, Blog.class);
                    allDeleted.add(blog);
                }
                if (!StringUtils.hasLength(title)) {
                    //以创建时间排序，由晚到早
                    allDeleted.sort((o1, o2) -> -o1.getCreated().compareTo(o2.getCreated()));
                    Page<Blog> page = MyUtils.listToPage(allDeleted, currentPage, size);
                    List<Blog> records = page.getRecords();
                    for (Blog record : records) {
                        record.setUsername(username);
                    }
                    page.setRecords(records);
                    return Result.succ(page);
                } else {
                    ArrayList<Blog> blogs = new ArrayList<>();
                    for (Blog blog : allDeleted) {
                        if (blog.getTitle().contains(title)) {
                            blogs.add(blog);
                        }
                    }
                    blogs.sort((o1, o2) -> -o1.getCreated().compareTo(o2.getCreated()));

                    Page<Blog> page = MyUtils.listToPage(blogs, currentPage, size);

                    List<Blog> records = page.getRecords();
                    for (Blog record : records) {
                        record.setUsername(username);
                    }
                    page.setRecords(records);
                    return Result.succ(page);
                }
            }
        }
        return Result.fail("读取失败");

    }

    /**
     * 恢复删除的博客
     * @param id
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/recoverBlogs/{id}/{userId}")
    public Result recoverBlog(@PathVariable(name = "id") Long id, @PathVariable(name = "userId") Long userId) {
        String key = userId + Const.QUERY_DELETED + id;
        LinkedHashMap<String, Object> value = (LinkedHashMap<String, Object>) redisTemplate.opsForValue().get(key);

        Blog blog = MyUtils.jsonToObj(value, Blog.class);

        //重新设置创建时间
        blog.setCreated(LocalDateTime.now());

        Assert.notNull(blog, "恢复异常");

        //用saveOrUpdate(blog)会导致id自增，这里要恢复原来的id
        boolean recover = blogService.recover(blog);

        log.info("恢复{}号日志结果:{}", id, recover);

        Assert.isTrue(recover, "恢复失败");

        redisTemplate.delete(key);

        //通知消息给mq
        MyUtils.sendBlogMessageToMQ(id, PostMQIndexMessage.CREATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_DELETED_PREFIX);

        return Result.succ(null);
    }






    /**
     * 更改文章状态，0为公开，1为登录后可阅读
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/modifyBlogStatus/{id}/{status}")
    public Result modifyBlogStatus(@PathVariable Long id, @PathVariable Integer status) {

        LocalDateTime created = blogService.getById(id).getCreated();

        boolean update = blogService.update(new UpdateWrapper<Blog>().eq("id", id).set("status", status).set("created", created));

        log.info("更改文章状态:{}", update);

        Assert.isTrue(update, "修改失败");

        //mq更新es
        MyUtils.sendBlogMessageToMQ(id, PostMQIndexMessage.UPDATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_BLOG_PREFIX);

        return Result.succ(null);
    }

    @RequiresRoles(value = {Const.ADMIN, Const.BOY, Const.GIRL, Const.GUEST}, logical = Logical.OR)
    @GetMapping("/getAllBlogs")
    @Cache(name = Const.HOT_BLOGS)//缓存页面信息一分钟
    public Result getAllBlogs(@RequestParam Integer currentPage, @RequestParam Integer size) {
        List<Blog> blogsList = blogService.queryAllBlogs();

        for (Blog blog : blogsList) {
            blog.setContent(blog.getContent().length() > 20 ? blog.getContent().substring(0, 20) : blog.getContent());
        }

        Page<Blog> page = MyUtils.listToPage(blogsList, currentPage, size);

        MyUtils.setRead(page);

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

        List<Blog> blogsList;

        //查询相关数据
        if (!StringUtils.hasLength(keyword)) {
            blogsList = blogService.queryAllBlogs();
        } else {

            MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description", "content");

            NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .must(multiMatchQueryBuilder))
                    .withSorts(SortBuilders.fieldSort("created").order(SortOrder.DESC))
                    .build();

            SearchHits<BlogPostDocument> search = elasticsearchRestTemplate.search(searchQueryHits, BlogPostDocument.class);

            blogsList = new ArrayList<>();

            for (SearchHit<BlogPostDocument> hit : search.getSearchHits()) {
                Blog blog = new Blog();

                MyUtils.documentToBlog(hit, blog);

                blogsList.add(blog);
            }
        }

        //只获取部分博客信息
        for (Blog blog : blogsList) {
            blog.setContent(blog.getContent().length() > 20 ? blog.getContent().substring(0, 20) : blog.getContent());
        }

        //将相关数据封装Page对象
        Page<Blog> page = MyUtils.listToPage(blogsList, currentPage, size);

        MyUtils.setRead(page);

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
        ArrayList<Long> idList = new ArrayList<>(List.of(ids));

        for (Long id : idList) {
            Blog blog = blogService.getById(id);

            //删除时间的设置
            blog.setCreated(LocalDateTime.now());

            redisTemplate.execute(new SessionCallback<>() {
                @Override
                public Object execute(@NonNull RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    operations.opsForHash().delete(Const.READ_SUM, id.toString());
                    operations.delete(Const.READ_RECENT + id);
                    operations.opsForValue().set(blog.getUserId() + Const.QUERY_DELETED + id, blog, 7 * 24 * 60, TimeUnit.MINUTES);
                    operations.exec();
                    return null;
                }
            });

            /*
             * 直接删除对应的文件夹，提高效率
             */
            String createdTime = blog.getCreated().toString();
            String created = createdTime
                    .replaceAll("-", "")
                    .replaceAll("T", "")
                    .replaceAll(":", "");
            String finalDest = baseFolderPath + img + "/" + created;
            File file = new File(finalDest);

            MyUtils.deleteAllImg(file);

            //删除文章
            boolean remove = blogService.removeById(id);

            log.info("数据库删除{}号博客结果:{}", id, remove);

            Assert.isTrue(remove, "删除失败");

            //通知消息给mq
            MyUtils.sendBlogMessageToMQ(id, PostMQIndexMessage.REMOVE);
        }

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_DELETED_PREFIX, Const.HOT_BLOG_PREFIX);

        return Result.succ("删除成功");
    }


    /**
     * 设置阅读密钥
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/setBlogToken")
    public Result setBlogToken() {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(Const.READ_TOKEN, token, 24, TimeUnit.HOURS);
        return Result.succ(null);
    }

    /**
     * 获取阅读密钥
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/getBlogToken")
    public Result getBlogToken() {
        String token;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.READ_TOKEN))) {
            token = (String) redisTemplate.opsForValue().get(Const.READ_TOKEN);
        } else {
            token = "阅读密钥目前没有设置";
        }

        if (token != null) {
            redisTemplate.opsForValue().set(Const.READ_TOKEN, token, 24, TimeUnit.HOURS);
        }

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
