package com.markerhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.common.exception.InsertOrUpdateErrorException;
import com.markerhub.common.lang.Const;
import com.markerhub.common.vo.BlogPostDocumentVo;
import com.markerhub.common.vo.BlogEntityVo;
import com.markerhub.config.RabbitConfig;
import com.markerhub.entity.BlogEntity;
import com.markerhub.entity.UserEntity;
import com.markerhub.mapper.BlogMapper;
import com.markerhub.mapper.UserMapper;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.service.UserService;
import com.markerhub.utils.MyUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.suggest.response.CompletionSuggestion;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, BlogEntity> implements BlogService {

    @Value("${uploadPath}")
    private String baseFolderPath;

    @Value("${imgFoldName}")
    private String img;

    UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ThreadPoolExecutor executor;

    @Autowired
    @Qualifier("pageThreadPoolExecutor")
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
    public List<BlogEntityVo> queryAllBlogs() {
        return blogMapper.queryAllBlogs();
    }

    @Override
    public boolean recover(BlogEntity blog) {
        return blogMapper.recover(blog);
    }

    @Override
    public Page<BlogEntity> listByYear(Integer currentPage, Integer year) {

        Page<BlogEntity> page = new Page<>(currentPage, Const.PAGE_SIZE);

        QueryWrapper<BlogEntity> queryWrapper = new QueryWrapper<>();
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);
        QueryWrapper<BlogEntity> wrapper = queryWrapper.select("id", "title", "description", "link", "created").between("created", start, end).orderByAsc("created");

        return page(page, wrapper);
    }

    @Override
    public Page<BlogEntity> listBlogsByPage(Integer currentPage) {
        Page<BlogEntity> page = new Page<>(currentPage, Const.PAGE_SIZE);
        return page(page, new QueryWrapper<BlogEntity>().select("id", "title", "description", "link", "created").orderByDesc("created"));
    }

    @Override
    public BlogEntity getBlogDetail(Long id) {
        BlogEntity blog = getOne(new QueryWrapper<BlogEntity>().eq("id", id).eq("status", 0));
        Assert.notNull(blog, "该博客不存在");
        MyUtils.setReadCount(id);
        return blog;
    }

    @Override
    public BlogEntity getAuthorizedBlogDetail(Long id) {
        BlogEntity blog = getById(id);
        Assert.notNull(blog, "该博客不存在");
        MyUtils.setReadCount(id);
        return blog;
    }

    @Override
    public BlogEntity getLockedBlog(Long blogId, String token) {
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
    public Page<BlogPostDocumentVo> selectBlogsByES(Integer currentPage, String keyword, Integer status) {

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


        CompletableFuture<SearchHits<BlogPostDocument>> searchHitsFuture = CompletableFuture.supplyAsync(() -> {


            NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .filter(QueryBuilders.termQuery("status", 0))
                            .must(multiMatchQueryBuilder))
                    .withSorts(SortBuilders.scoreSort())
                    .withPageable(PageRequest.of(currentPage - 1, Const.PAGE_SIZE));

            NativeSearchQuery searchQueryHits;

            if (status == 0) {

                searchQueryHits = builder.withHighlightBuilder(new HighlightBuilder()
                        .field("title").field("description").field("content").preTags("<b style='color:red'>").postTags("</b>").fragmentSize(5).numOfFragments(1)).build();
            } else {
                searchQueryHits = builder.withHighlightBuilder(new HighlightBuilder()
                        .field("title").field("description").field("content").preTags("<b style='color:red'>").postTags("</b>")).build();
            }

            return elasticsearchRestTemplate.search(searchQueryHits, BlogPostDocument.class);


        }, executor);

        //主线程在这等着
        CompletableFuture.allOf(countFuture, searchHitsFuture).get();

        //拿到线程池两个线程的执行结果
        SearchHits<BlogPostDocument> search = searchHitsFuture.get();
        Long count = countFuture.get();

        Page<BlogPostDocumentVo> page = MyUtils.hitsToPage(search, BlogPostDocumentVo.class, currentPage, Const.PAGE_SIZE, count);

        for (BlogPostDocumentVo record : page.getRecords()) {
            record.setContent(null);
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }

        return page;
    }

    @SneakyThrows
    @Override
    public Page<BlogPostDocumentVo> selectYearBlogsByES(Integer currentPage, String keyword, Integer year, Integer status) {
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

            NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.boolQuery()
                            .filter(QueryBuilders.rangeQuery("created").gte(year + "-01-01T00:00:00").lte(year + "-12-31T23:59:59")
                                    .includeUpper(true)
                                    .includeLower(true))
                            .must(multiMatchQueryBuilder)
                            .filter(QueryBuilders.termQuery("status", 0)))
                    .withSorts(SortBuilders.scoreSort())
                    .withPageable(PageRequest.of(currentPage - 1, Const.PAGE_SIZE));

            NativeSearchQuery searchQueryHits;

            if (status == 0) {
                searchQueryHits = builder.withHighlightBuilder(new HighlightBuilder()
                        .field("title").field("description").field("content").preTags("<b style='color:red'>").postTags("</b>").fragmentSize(5).numOfFragments(1))
                        .build();
            } else {
                searchQueryHits = builder.withHighlightBuilder(new HighlightBuilder()
                                .field("title").field("description").field("content").preTags("<b style='color:red'>").postTags("</b>"))
                        .build();
            }

            return elasticsearchRestTemplate.search(searchQueryHits, BlogPostDocument.class);
        }, executor);

        CompletableFuture.allOf(countFuture, searchHitsFuture).get();

        long count = countFuture.get();
        SearchHits<BlogPostDocument> search = searchHitsFuture.get();

        Page<BlogPostDocumentVo> page = MyUtils.hitsToPage(search, BlogPostDocumentVo.class, currentPage, Const.PAGE_SIZE, count);

        for (BlogPostDocumentVo record : page.getRecords()) {
            record.setContent(null);
            record.setCreated(record.getCreated().plusHours(Const.GMT_PLUS_8));
        }

        return page;
    }

    @SneakyThrows
    @Override
    public void updateBlog(BlogEntityVo blog) {

        //都是更新，之前初始化过了

        BlogEntity temp = getById(blog.getId());

        UserEntity user = userService.getOne(new QueryWrapper<UserEntity>().select("username").eq("id", temp.getUserId()));

        // 只能编辑自己的文章
        Assert.isTrue(Objects.equals(user.getUsername(), SecurityContextHolder.getContext().getAuthentication().getName()), "没有权限编辑");

        BeanUtils.copyProperties(blog, temp, "id", "userId", "created");
        boolean update = saveOrUpdate(temp);

        log.info("数据库更新{}号博客结果:{}", blog.getId(), update);

        Assert.isTrue(update, "更新失败");

        //通知消息给mq,更新并删除缓存
        CorrelationData correlationData = new CorrelationData();
        //防止重复消费
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.UPDATE + "_" + blog.getId(), 30, TimeUnit.MINUTES);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(blog.getId(), PostMQIndexMessage.UPDATE), correlationData);

    }

    @Override
    public Long initBlog() {
        BlogEntity blog = new BlogEntity();

        MyUtils.initBlog(blog);

        boolean add = saveOrUpdate(blog);

        log.info("初始化博客结果:{}", add);
        Assert.isTrue(add, "初始化博客失败");

        //设置bloomFilter
        redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_BLOG, blog.getId(), true);

        //删除缓存
        Set<String> keys = redisTemplate.keys(Const.HOT_BLOGS_PREFIX);

        if (keys != null) {
            redisTemplate.unlink(keys);
        }

        //通知消息给mq，创建
        //防止重复消费
        CorrelationData correlationData = new CorrelationData();
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.CREATE + "_" + blog.getId(), 30, TimeUnit.MINUTES);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(blog.getId(), PostMQIndexMessage.CREATE), correlationData);


        return blog.getId();
    }

    @Override
    public Page<BlogEntityVo> selectDeletedBlogs(String title, Integer currentPage, Integer size, Long userId) {

        UserEntity one = userMapper.selectOne(new QueryWrapper<UserEntity>().eq("id", userId));
        String username = one.getUsername();

        String prefix = userId + Const.QUERY_ALL_DELETED;
        Set<String> keys = redisTemplate.keys(prefix);

        if (keys != null) {
            List<Object> rawAllDeleted = redisTemplate.opsForValue().multiGet(keys);
            if (rawAllDeleted != null) {
                ArrayList<BlogEntityVo> allDeleted = new ArrayList<>();
                for (Object value : rawAllDeleted) {
                    BlogEntityVo blog = MyUtils.jsonToObj(value, BlogEntityVo.class);
                    allDeleted.add(blog);
                }
                if (!StringUtils.hasLength(title)) {
                    //以创建时间排序，由晚到早
                    allDeleted.sort((o1, o2) -> -o1.getCreated().compareTo(o2.getCreated()));
                    Page<BlogEntityVo> page = MyUtils.listToPage(allDeleted, currentPage, size);
                    List<BlogEntityVo> records = page.getRecords();
                    records.forEach(record -> record.setUsername(username));
                    page.setRecords(records);
                    return page;
                } else {
                    ArrayList<BlogEntityVo> blogs = new ArrayList<>();
                    allDeleted.stream().filter(blog -> blog.getTitle().contains(title)).forEach(blogs::add);
                    blogs.sort((o1, o2) -> -o1.getCreated().compareTo(o2.getCreated()));
                    Page<BlogEntityVo> page = MyUtils.listToPage(blogs, currentPage, size);
                    List<BlogEntityVo> records = page.getRecords();
                    records.forEach(record -> record.setUsername(username));
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

        BlogEntity blog = MyUtils.jsonToObj(value, BlogEntity.class);

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
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.CREATE + "_" + id, 30, TimeUnit.MINUTES);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(id, PostMQIndexMessage.CREATE), correlationData);

    }

    @Override
    @Transactional
    public void changeBlogStatus(Long id, Integer status) {
        LocalDateTime created = getById(id).getCreated();

        boolean update = update(new UpdateWrapper<BlogEntity>().eq("id", id).set("status", status).set("created", created));

        log.info("更改文章状态:{}", update);

        Assert.isTrue(update, "修改失败");

        //通知消息给mq,更新并删除缓存
        CorrelationData correlationData = new CorrelationData();
        //防止重复消费
        redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.UPDATE + "_" + id, 30, TimeUnit.MINUTES);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(id, PostMQIndexMessage.UPDATE), correlationData);
    }

    @Override
    public Page<BlogEntityVo> getAllBlogs(Integer currentPage, Integer size) {
        List<BlogEntityVo> blogsList = queryAllBlogs();
        blogsList.forEach(blog -> blog.setContent(blog.getContent().length() > 20 ? blog.getContent().substring(0, 20) : blog.getContent()));
        Page<BlogEntityVo> page = MyUtils.listToPage(blogsList, currentPage, size);
        MyUtils.setRead(page);
        return page;
    }

    @Override
    public Page<BlogEntityVo> queryBlogsAbstract(String keyword, Integer currentPage, Integer size) {
        List<BlogEntityVo> blogsList;

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

            search.getSearchHits().forEach(hit -> {
                BlogEntityVo blog = new BlogEntityVo();
                MyUtils.documentToBlog(hit, blog);
                blogsList.add(blog);
            });
        }

        //只获取部分博客信息
        blogsList.forEach(blog -> blog.setContent(blog.getContent().length() > 20 ? blog.getContent().substring(0, 20) : blog.getContent()));

        //将相关数据封装Page对象
        Page<BlogEntityVo> page = MyUtils.listToPage(blogsList, currentPage, size);

        MyUtils.setRead(page);
        return page;
    }

    @Override
    @Transactional
    public void deleteBlogs(Long[] ids) {
        ArrayList<Long> idList = new ArrayList<>(List.of(ids));

        idList.forEach(id -> {
            BlogEntity blog = getById(id);
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

            MyUtils.deleteAllImg(file);

            //通知消息给mq,更新并删除缓存
            CorrelationData correlationData = new CorrelationData();
            //防止重复消费
            redisTemplate.opsForValue().set(Const.CONSUME_MONITOR + correlationData.getId(), PostMQIndexMessage.REMOVE + "_" + id, 30, TimeUnit.MINUTES);

            rabbitTemplate.convertAndSend(
                    RabbitConfig.ES_EXCHANGE,
                    RabbitConfig.ES_BINDING_KEY,
                    new PostMQIndexMessage(id, PostMQIndexMessage.REMOVE), correlationData);
        });
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
