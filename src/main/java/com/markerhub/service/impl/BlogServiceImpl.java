package com.markerhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.exception.InsertOrUpdateErrorException;
import com.markerhub.common.lang.Const;
import com.markerhub.common.vo.BlogPostDocumentVo;
import com.markerhub.common.vo.BlogVo;
import com.markerhub.config.RabbitConfig;
import com.markerhub.entity.Blog;
import com.markerhub.entity.User;
import com.markerhub.mapper.BlogMapper;
import com.markerhub.mapper.UserMapper;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.util.MyUtil;
import com.markerhub.util.ShiroUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
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

    ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ThreadPoolExecutor executor;

    @Autowired
    public void setExecutor(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(RabbitTemplate RabbitTemplate) {
        this.rabbitTemplate = RabbitTemplate;
    }

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
    public Integer getYearCount(Integer year) {
        return blogMapper.getYearCount(year);
    }

    @Override
    public List<BlogVo> queryAllBlogs() {
        return blogMapper.queryAllBlogs();
    }

    @Override
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

        long count = count();

        long totalPage = count % Const.PAGE_SIZE == 0 ? count / Const.PAGE_SIZE : count / Const.PAGE_SIZE + 1;

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
    public Blog getBlogDetail(Long id) {

        if (getById(id).getStatus() == 1) {
            throw new AuthenticationException("没有访问权限");
        }

        Blog blog = getOne(new QueryWrapper<Blog>().eq("id", id).eq("status", 0));

        Assert.notNull(blog, "该博客不存在");

        MyUtil.setReadCount(id);

        return blog;
    }

    @Override
    public Blog getAuthorizedBlogDetail(Long id) {
        Blog blog = getById(id);

        Assert.notNull(blog, "该博客不存在");

        MyUtil.setReadCount(id);

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

    @SneakyThrows
    @Override
    public Page<BlogPostDocumentVo> selectBlogsByES(Integer currentPage, String keyword) {

        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description", "content");

        //启动线程1
        CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(() -> {

            NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .filter(QueryBuilders.termQuery("status", 0))
                            .must(multiMatchQueryBuilder))
                    .build();

            return elasticsearchRestTemplate.count(searchQueryCount, BlogPostDocument.class);

        }, executor);

        //启动线程2
        CompletableFuture<SearchHits<BlogPostDocument>> searchHitsFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .filter(QueryBuilders.termQuery("status", 0))
                            .must(multiMatchQueryBuilder))
                    .withSorts(SortBuilders.scoreSort())
                    .withPageable(PageRequest.of(currentPage - 1, Const.PAGE_SIZE))
                    .withHighlightBuilder(new HighlightBuilder()
                            .field("title").field("description").field("content").preTags("<b style='color:red'>").postTags("</b>"))
                    .build();

            return elasticsearchRestTemplate.search(searchQueryHits, BlogPostDocument.class);
        }, executor);

        //主线程在这等着
        CompletableFuture.allOf(countFuture, searchHitsFuture).get();

        //拿到线程池两个线程的执行结果
        SearchHits<BlogPostDocument> search = searchHitsFuture.get();
        Long count = countFuture.get();

        Page<BlogPostDocumentVo> page = MyUtil.hitsToPage(search, BlogPostDocumentVo.class, currentPage, Const.PAGE_SIZE, count);

        for (BlogPostDocumentVo record : page.getRecords()) {
            record.setContent(null);
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }

        log.info("{} 关键词被首页搜索", keyword);

        return page;
    }

    @SneakyThrows
    @Override
    public Page<BlogPostDocumentVo> selectYearBlogsByES(Integer currentPage, String keyword, Integer year) {
        MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "description", "content");

        CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryCount = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .filter(QueryBuilders.rangeQuery("created").gte(year + "-01-01T00:00:00").lte(year + "-12-31T23:59:59")
                                    .includeUpper(true)
                                    .includeLower(true))
                            .must(multiMatchQueryBuilder)
                            .filter(QueryBuilders.termQuery("status", 0)))
                    .build();

            return elasticsearchRestTemplate.count(searchQueryCount, BlogPostDocument.class);
        }, executor);


        CompletableFuture<SearchHits<BlogPostDocument>> searchHitsFuture = CompletableFuture.supplyAsync(() -> {
            NativeSearchQuery searchQueryHits = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .filter(QueryBuilders.rangeQuery("created").gte(year + "-01-01T00:00:00").lte(year + "-12-31T23:59:59")
                                    .includeUpper(true)
                                    .includeLower(true))
                            .must(multiMatchQueryBuilder)
                            .filter(QueryBuilders.termQuery("status", 0)))
                    .withSorts(SortBuilders.scoreSort())
                    .withPageable(PageRequest.of(currentPage - 1, Const.PAGE_SIZE))
                    .withHighlightBuilder(new HighlightBuilder()
                            .field("title").field("description").field("content").preTags("<b style='color:red'>").postTags("</b>"))
                    .build();

            return elasticsearchRestTemplate.search(searchQueryHits, BlogPostDocument.class);
        }, executor);

        CompletableFuture.allOf(countFuture, searchHitsFuture);

        long count = countFuture.get();
        SearchHits<BlogPostDocument> search = searchHitsFuture.get();

        Page<BlogPostDocumentVo> page = MyUtil.hitsToPage(search, BlogPostDocumentVo.class, currentPage, Const.PAGE_SIZE, count);

        for (BlogPostDocumentVo record : page.getRecords()) {
            record.setContent(null);
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }

        log.info("{} 关键词被{}年界面搜索", keyword, year);

        return page;
    }

    @SneakyThrows
    @Override
    public void updateBlog(BlogVo blog) {

        //都是更新，之前初始化过了

        Blog temp = getById(blog.getId());
        // 只能编辑自己的文章
        Assert.isTrue(temp.getUserId().longValue() == ShiroUtil.getProfile().getId().longValue(), "没有权限编辑");

        BeanUtil.copyProperties(blog, temp, "id", "userId", "created", "status");
        boolean update = updateById(temp);

        log.info("数据库更新{}号博客结果:{}", blog.getId(), update);

        if (!update) {
            throw new InsertOrUpdateErrorException("更新失败");
        }


        //通知消息给mq,更新并删除缓存
        CorrelationData correlationData = new CorrelationData();
        //防止重复消费
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.UPDATE + "_" + blog.getId());

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(blog.getId(), PostMQIndexMessage.UPDATE), correlationData);

    }

    @Override
    @Transactional
    public Long initBlog() {
        Blog blog = new Blog();

        MyUtil.initBlog(blog);

        boolean add = save(blog);

        log.info("初始化博客结果:{}", add);

        if (!add) {
            throw new InsertOrUpdateErrorException("初始化博客失败");
        }

        //设置bloomFilter
        redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_BLOG, blog.getId(), true);

        //删除缓存
        Set<String> keys = redisTemplate.keys(Const.HOT_BLOGS_PREFIX);

        if (keys != null) {
            redisTemplate.delete(keys);
        }

        //通知消息给mq，创建
        //防止重复消费
        CorrelationData correlationData = new CorrelationData();
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.CREATE + "_" + blog.getId());

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(blog.getId(), PostMQIndexMessage.CREATE), correlationData);


        return blog.getId();
    }

    @Override
    public Page<BlogVo> selectDeletedBlogs(String title, Integer currentPage, Integer size, Long userId) {

        User one = userMapper.selectOne(new QueryWrapper<User>().eq("id", userId));
        String username = one.getUsername();

        String prefix = userId + Const.QUERY_ALL_DELETED;
        Set<String> keys = redisTemplate.keys(prefix);

        if (keys != null) {
            List<Object> rawAllDeleted = redisTemplate.opsForValue().multiGet(keys);
            if (rawAllDeleted != null) {
                ArrayList<BlogVo> allDeleted = new ArrayList<>();
                for (Object value : rawAllDeleted) {
                    BlogVo blog = MyUtil.jsonToObj(value, BlogVo.class);
                    allDeleted.add(blog);
                }
                if (!StringUtils.hasLength(title)) {
                    //以创建时间排序，由晚到早
                    allDeleted.sort((o1, o2) -> -o1.getCreated().compareTo(o2.getCreated()));
                    Page<BlogVo> page = MyUtil.listToPage(allDeleted, currentPage, size);
                    List<BlogVo> records = page.getRecords();
                    for (BlogVo record : records) {
                        record.setUsername(username);
                    }
                    page.setRecords(records);
                    return page;
                } else {
                    ArrayList<BlogVo> blogs = new ArrayList<>();
                    for (BlogVo blog : allDeleted) {
                        if (blog.getTitle().contains(title)) {
                            blogs.add(blog);
                        }
                    }
                    blogs.sort((o1, o2) -> -o1.getCreated().compareTo(o2.getCreated()));

                    Page<BlogVo> page = MyUtil.listToPage(blogs, currentPage, size);

                    List<BlogVo> records = page.getRecords();
                    for (BlogVo record : records) {
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
    @Transactional
    public void recoverBlog(Long id, Long userId) {
        String key = userId + Const.QUERY_DELETED + id;
        LinkedHashMap<String, Object> value = (LinkedHashMap<String, Object>) redisTemplate.opsForValue().get(key);

        Blog blog = MyUtil.jsonToObj(value, Blog.class);

        Assert.notNull(blog, "恢复异常");

        //重新设置创建时间
        blog.setCreated(LocalDateTime.now());

        //用saveOrUpdate(blog)会导致id自增，这里要恢复原来的id
        boolean recover = recover(blog);

        log.info("恢复{}号日志结果:{}", id, recover);

        Assert.isTrue(recover, "恢复失败");

        //设置bloomFilter
        redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_BLOG, blog.getId(), true);

        redisTemplate.delete(key);

        //通知消息给mq,更新
        CorrelationData correlationData = new CorrelationData();
        //防止重复消费
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.CREATE + "_" + id);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(id, PostMQIndexMessage.CREATE), correlationData);

    }

    @Override
    @Transactional
    public void changeBlogStatus(Long id, Integer status) {
        LocalDateTime created = getById(id).getCreated();

        boolean update = update(new UpdateWrapper<Blog>().eq("id", id).set("status", status).set("created", created));

        log.info("更改文章状态:{}", update);

        Assert.isTrue(update, "修改失败");

        //通知消息给mq,更新并删除缓存
        CorrelationData correlationData = new CorrelationData();
        //防止重复消费
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.UPDATE + "_" + id);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(id, PostMQIndexMessage.UPDATE), correlationData);
    }

    @Override
    public Page<BlogVo> getAllBlogs(Integer currentPage, Integer size) {
        List<BlogVo> blogsList = queryAllBlogs();

        for (Blog blog : blogsList) {
            blog.setContent(blog.getContent().length() > 20 ? blog.getContent().substring(0, 20) : blog.getContent());
        }

        Page<BlogVo> page = MyUtil.listToPage(blogsList, currentPage, size);

        MyUtil.setRead(page);

        return page;
    }

    @Override
    public Page<BlogVo> queryBlogsAbstract(String keyword, Integer currentPage, Integer size) {
        List<BlogVo> blogsList;

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
                BlogVo blog = new BlogVo();

                MyUtil.documentToBlog(hit, blog);

                blogsList.add(blog);
            }
        }

        //只获取部分博客信息
        for (BlogVo blog : blogsList) {
            blog.setContent(blog.getContent().length() > 20 ? blog.getContent().substring(0, 20) : blog.getContent());
        }

        //将相关数据封装Page对象
        Page<BlogVo> page = MyUtil.listToPage(blogsList, currentPage, size);

        MyUtil.setRead(page);

        return page;
    }

    @Override
    @Transactional
    public void deleteBlogs(Long[] ids) {
        ArrayList<Long> idList = new ArrayList<>(List.of(ids));

        for (Long id : idList) {
            Blog blog = getById(id);

            //删除文章
            boolean remove = removeById(id);

            log.info("数据库删除{}号博客结果:{}", id, remove);

            Assert.isTrue(remove, "删除失败");

            //更改bloomFilter
            redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_BLOG, blog.getId(), false);

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

            MyUtil.deleteAllImg(file);

            //通知消息给mq,更新并删除缓存
            CorrelationData correlationData = new CorrelationData();
            //防止重复消费
            redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.REMOVE + "_" + id);

            rabbitTemplate.convertAndSend(
                    RabbitConfig.ES_EXCHANGE,
                    RabbitConfig.ES_BINDING_KEY,
                    new PostMQIndexMessage(id, PostMQIndexMessage.REMOVE), correlationData);

        }
    }

    @Override
    public String getBlogToken() {
        String token = (String) redisTemplate.opsForValue().get(Const.READ_TOKEN);

       if (token == null) {
           token = "阅读密钥目前没有设置";
       }

       redisTemplate.opsForValue().set(Const.READ_TOKEN, token, 24, TimeUnit.HOURS);
       return token;
    }

    @Override
    public void setBlogToken() {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(Const.READ_TOKEN, token, 24, TimeUnit.HOURS);
    }

    @Override
    public int[] searchYears() {
        List<Integer> in = blogMapper.searchYears();
        int[] out = new int[in.size()];
        for (int i = 0; i < in.size(); i++) {
            out[i] = in.get(i);
        }
        return out;
    }

}
