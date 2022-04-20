package com.markerhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.exception.InsertOrUpdateErrorException;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.Blog;
import com.markerhub.entity.User;
import com.markerhub.mapper.BlogMapper;
import com.markerhub.mapper.UserMapper;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.util.MyUtils;
import com.markerhub.util.ShiroUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Value("${uploadPath}")
    private String baseFolderPath;

    @Value("${imgFoldName}")
    private String img;

    ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    public void setElasticsearchRestTemplate(ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private void setRedisTemplateImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    BlogMapper blogMapper;

    @Autowired
    private void setBlogMapper(BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
    }

    UserMapper userMapper;

    @Autowired
    public void setUserMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }


    @Override
    @Transactional
    public Integer getYearCount(Integer year) {
        return blogMapper.getYearCount(year);
    }

    @Override
    @Transactional
    public List<Blog> queryAllBlogs() {
        return blogMapper.queryAllBlogs();
    }

    @Override
    @Transactional
    public List<Blog> queryBlogs(String title) {
        return blogMapper.queryBlogs(title);
    }

    @Override
    @Transactional
    public boolean recover(Blog blog) {
        return blogMapper.recover(blog);
    }

    @Override
    public Page<Blog> listByYear(Integer currentPage, Integer year) {

        Page<Blog> page = new Page<>(currentPage, Const.PAGE_SIZE);
        QueryWrapper<Blog> queryWrapper = new QueryWrapper<>();
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);

        QueryWrapper<Blog> wrapper = queryWrapper.select("id", "title", "description", "link", "created").between("created", start, end).orderByAsc("created");

        return page(page, wrapper);
    }

    @Override
    public Page<Blog> listBlogsByPage(Integer currentPage) {

        int totalPage = count(null) % Const.PAGE_SIZE == 0 ? count(null) / Const.PAGE_SIZE : count(null) / Const.PAGE_SIZE + 1;

        if (currentPage > totalPage) {
            Page<Blog> page = new Page<>(totalPage, Const.PAGE_SIZE);
            return page(page, new QueryWrapper<Blog>().select("title", "description", "link", "created").orderByDesc("created"));
        }

        if (totalPage < 1) {
            Page<Blog> page = new Page<>(1, Const.PAGE_SIZE);
            return page(page, new QueryWrapper<Blog>().select("title", "description", "link", "created").orderByDesc("created"));
        }

        Page<Blog> page = new Page<>(currentPage, Const.PAGE_SIZE);

        return page(page, new QueryWrapper<Blog>().select("id", "title", "description", "link", "created").orderByDesc("created"));
    }

    @Override
    @Transactional
    public Blog getBlogDetail(Long id) {

        if (getById(id).getStatus() == 1) {
            throw new AuthenticationException("没有访问权限");
        }

        Blog blog = getOne(new QueryWrapper<Blog>().eq("status", 0).eq("id", id));

        Assert.notNull(blog, "该博客不存在");

        MyUtils.setReadCount(id);

        return blog;
    }

    @Override
    @Transactional
    public Blog getAuthorizedBlogDetail(Long id) {
        Blog blog = getById(id);

        Assert.notNull(blog, "该博客不存在");

        MyUtils.setReadCount(id);

        return blog;
    }

    @Override
    public Blog getLockedBlog(Long blogId, String token) {
        token = token.trim();
        String exist = (String) redisTemplate.opsForValue().get(Const.READ_TOKEN);
        if (StringUtils.hasLength(token) && StringUtils.hasLength(exist)) {
            if (!token.equals(exist)) {
                return null;
            } else {
                return getById(blogId);
            }
        } else {
            return null;
        }
    }

    @Override
    public Page<BlogPostDocument> selectBlogsByES(Integer currentPage, String keyword) {
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

        return page;
    }

    @Override
    public Page<BlogPostDocument> selectYearBlogsByES(Integer currentPage, String keyword, Integer year) {
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

        return page;
    }

    @Override
    public void updateBlog(Blog blog) {


        //都是更新，之前初始化过了

        Blog temp = getById(blog.getId());
        // 只能编辑自己的文章
        Assert.isTrue(temp.getUserId().longValue() == ShiroUtil.getProfile().getId().longValue(), "没有权限编辑");

        BeanUtil.copyProperties(blog, temp, "id", "userId", "created", "status");
        boolean update = saveOrUpdate(temp);

        log.info("数据库更新{}号博客结果:{}", blog.getId(), update);

        if (!update) {
            throw new InsertOrUpdateErrorException("更新失败");
        }

        //通知消息给mq,更新
        MyUtils.sendBlogMessageToMQ(blog.getId(), PostMQIndexMessage.UPDATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_BLOG_PREFIX);

    }

    @Override
    public Long initBlog() {
        Blog blog = new Blog();

        MyUtils.initBlog(blog);

        boolean add = saveOrUpdate(blog);

        log.info("初始化博客结果:{}", add);

        //通知消息给mq
        MyUtils.sendBlogMessageToMQ(blog.getId(), PostMQIndexMessage.CREATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_BLOG_PREFIX);

        if (!add) {
            throw new InsertOrUpdateErrorException("初始化博客失败");
        } else {
            return blog.getId();
        }
    }

    @Override
    public Page<Blog> selectDeletedBlogs(String title, Integer currentPage, Integer size, Long userId) {

        User one = userMapper.selectOne(new QueryWrapper<User>().eq("id", userId).last("LIMIT 1"));
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
                    return page;
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
                    return page;
                }
            }
        }
        return null;
    }

    @Override
    public void recoverBlog(Long id, Long userId) {
        String key = userId + Const.QUERY_DELETED + id;
        LinkedHashMap<String, Object> value = (LinkedHashMap<String, Object>) redisTemplate.opsForValue().get(key);

        Blog blog = MyUtils.jsonToObj(value, Blog.class);

        //重新设置创建时间
        blog.setCreated(LocalDateTime.now());

        Assert.notNull(blog, "恢复异常");

        //用saveOrUpdate(blog)会导致id自增，这里要恢复原来的id
        boolean recover = recover(blog);

        log.info("恢复{}号日志结果:{}", id, recover);

        Assert.isTrue(recover, "恢复失败");

        redisTemplate.delete(key);

        //通知消息给mq
        MyUtils.sendBlogMessageToMQ(id, PostMQIndexMessage.CREATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_DELETED_PREFIX);
    }

    @Override
    public void changeBlogStatus(Long id, Integer status) {
        LocalDateTime created = getById(id).getCreated();

        boolean update = update(new UpdateWrapper<Blog>().eq("id", id).set("status", status).set("created", created));

        log.info("更改文章状态:{}", update);

        Assert.isTrue(update, "修改失败");

        //mq更新es
        MyUtils.sendBlogMessageToMQ(id, PostMQIndexMessage.UPDATE);

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_BLOGS_PREFIX, Const.HOT_BLOG_PREFIX);
    }

    @Override
    public Page<Blog> getAllBlogs(Integer currentPage, Integer size) {
        List<Blog> blogsList = queryAllBlogs();

        for (Blog blog : blogsList) {
            blog.setContent(blog.getContent().length() > 20 ? blog.getContent().substring(0, 20) : blog.getContent());
        }

        Page<Blog> page = MyUtils.listToPage(blogsList, currentPage, size);

        MyUtils.setRead(page);

        return page;
    }

    @Override
    public Page<Blog> queryBlogsAbstract(String keyword, Integer currentPage, Integer size) {
        List<Blog> blogsList;

        //查询相关数据
        if (!StringUtils.hasLength(keyword)) {
            blogsList = queryAllBlogs();
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

        return page;
    }

    @Override
    public void deleteBlogs(Long[] ids) {
        ArrayList<Long> idList = new ArrayList<>(List.of(ids));

        for (Long id : idList) {
            Blog blog = getById(id);

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
            boolean remove = removeById(id);

            log.info("数据库删除{}号博客结果:{}", id, remove);

            Assert.isTrue(remove, "删除失败");

            //通知消息给mq
            MyUtils.sendBlogMessageToMQ(id, PostMQIndexMessage.REMOVE);
        }

        //删除缓存热点
        MyUtils.deleteHot(Const.HOT_DELETED_PREFIX, Const.HOT_BLOG_PREFIX);
    }

    @Override
    public String getBlogToken() {
        String token;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.READ_TOKEN))) {
            token = (String) redisTemplate.opsForValue().get(Const.READ_TOKEN);
        } else {
            token = "阅读密钥目前没有设置";
        }

        if (token != null) {
            redisTemplate.opsForValue().set(Const.READ_TOKEN, token, 24, TimeUnit.HOURS);
        }
        return token;
    }

    @Override
    public void setBlogToken() {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(Const.READ_TOKEN, token, 24, TimeUnit.HOURS);
    }


}
