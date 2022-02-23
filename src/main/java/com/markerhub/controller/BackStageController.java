package com.markerhub.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.cache.Cache;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Blog;
import com.markerhub.entity.User;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.model.mq.PostMQIndexMessage;
import com.markerhub.service.BlogService;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtils;
import com.markerhub.util.MyUtils;
import com.markerhub.util.ShiroUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 后台功能
 * @author mingchiuli
 * @create 2021-12-06 8:10 PM
 */
@RestController
@Slf4j
public class BackStageController {

    @Value("${uploadPath}")
    private String baseFolderPath;

    @Value("${imgFoldName}")
    private String img;

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    public void setElasticsearchRestTemplate(ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
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

    JwtUtils jwtUtils;

    @Autowired
    private void setJwtUtils(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    AmqpTemplate amqpTemplate;

    @Autowired
    public void setAmqpTemplate(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
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
     * 更改账户状态，0可以正常使用，1禁用
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/modifyUser/{id}/{status}")
    public Result modifyUser(@PathVariable Integer id, @PathVariable Integer status) {

        boolean update = userService.update(new UpdateWrapper<User>().eq("id", id).set("status", status));

        log.info("更新账户状态:{}", update);

        Assert.isTrue(update, "删除失败");

        MyUtils.deleteHot(Const.HOT_USERS_PREFIX, Const.HOT_BLOG_PREFIX);

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
     * 查询账号
     */
    @RequiresRoles(value = {Const.ADMIN, Const.BOY, Const.GIRL, Const.GUEST}, logical = Logical.OR)
    @Cache(name = Const.HOT_USERS)
    @GetMapping("/queryUsers")
    public Result queryUsers(@RequestParam String role, @RequestParam Integer currentPage, @RequestParam Integer size) {

        Page<User> userPage = new Page<>(currentPage, size);
        Page<User> page;
        if (StringUtils.hasLength(role)) {//搜索
            page = userService.page(userPage, new QueryWrapper<User>().eq("role", role));

        } else {//不是搜索
            page = userService.page(userPage, new QueryWrapper<User>().select("id", "username", "avatar", "email", "status", "created", "last_login", "role").orderByAsc("created"));
        }
        List<User> records = page.getRecords();
        for (User record : records) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.USER_PREFIX + record.getId())) && record.getStatus() == 0) {
                record.setMonitor(1);
            } else {
                record.setMonitor(0);
            }
        }
        page.setRecords(records);
        return Result.succ(page);
    }

    /**
     * 新增账号，修改信息
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/addUser")
    public Result addUser(@Validated @RequestBody User user) {

        User userExist = userService.getBaseMapper().selectOne(new QueryWrapper<User>().eq("id", user.getId()));

        if (userExist == null) {//添加
            user.setPassword(SecureUtil.md5(user.getPassword()));
            user.setCreated(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());
            boolean update = userService.saveOrUpdate(user);

            log.info("添加{}号账号结果:{}", user.getId(), update);

            Assert.isTrue(update, "添加失败");
        } else {//修改

            BeanUtil.copyProperties(user, userExist, "created", "password", "lastLogin", "username", "id");

            boolean update = userService.saveOrUpdate(userExist);

            log.info("修改{}号账号结果:{}", userExist.getId(), update);

            Assert.isTrue(update, "修改失败");
            //删除缓存角色授权信息
            redisTemplate.delete(Const.ROLE_PREFIX + user.getId());
        }

        MyUtils.deleteHot(Const.HOT_USERS_PREFIX);

        return Result.succ(null);
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
     * 删除账号，批量
     * @param ids
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/deleteUsers")
    public Result deleteUsers(@RequestBody Long[] ids) {

        ArrayList<Long> idList = new ArrayList<>(List.of(ids));

        boolean remove = userService.removeByIds(idList);

        log.info("删除账号{}结果:{}", ids, remove);

        Assert.isTrue(remove, "删除失败");

        MyUtils.deleteHot(Const.HOT_USERS_PREFIX);

        return Result.succ(null);
    }

    /**
     * 回显信息
     * @param id
     * @return
     */
    @RequiresAuthentication
    @GetMapping("/getInfoById/{id}")
    public Result getRoleId(@PathVariable Long id) {
        User user = userService.getBaseMapper().selectOne(new QueryWrapper<User>().eq("id", id).select("id", "username", "email", "role", "avatar", "status"));
        return Result.succ(user);
    }

    /**
     * 踢人下线
     * @param id
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/roleKick/{id}")
    public Result roleKick(@PathVariable Long id) {

        //先进行锁定
        boolean update = userService.update(new UpdateWrapper<User>().eq("id", id).set("status", 1));

        log.info("锁定账号{}结果:{}", id, update);

        Assert.isTrue(update, "锁定失败");
        //再对缓存进行更新赋值操作
        User user = userService.getById(id);
        String jwt = jwtUtils.generateToken(id);

        //替换掉原来的user会话
        MyUtils.setUserToCache(jwt, user, (long) (6 * 10 * 60));

        return Result.succ(null);
    }


    /**
     * 密码修改
     * @param passwordDto
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/modifyPassword")
    public Result getPassword(@Validated @RequestBody PasswordDto passwordDto) {

        boolean update = userService.update(new UpdateWrapper<User>().eq("username", passwordDto.getUsername()).set("password", SecureUtil.md5(passwordDto.getPassword())));

        log.info("修改{}密码结果:{}", passwordDto.getUsername(), update);

        Assert.isTrue(update, "修改密码失败");

        return Result.succ(null);
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
