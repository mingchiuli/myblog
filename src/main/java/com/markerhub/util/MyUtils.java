package com.markerhub.util;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.config.RabbitConfig;
import com.markerhub.entity.Blog;
import com.markerhub.entity.User;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.search.mq.PostMQIndexMessage;
import com.markerhub.service.UserService;
import io.jsonwebtoken.Claims;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * @author mingchiuli
 * @create 2021-11-02 8:55 PM
 */
public class MyUtils {


    public static Long reqToUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");

        JwtUtils jwtUtils = SpringUtil.getBean(JwtUtils.class);

        if(!StringUtils.hasLength(token)) {
            return (long) -1;
        } else {
            // 教验jwt
            Claims claim = jwtUtils.getClaimByToken(token);

            String userId = Objects.requireNonNull(claim).getSubject();

            return Long.valueOf(userId);
        }
    }

    /**
     * 设置文章阅读量数据
     * @param id
     */
    public static void setReadCount(Long id) {

        RedisTemplate<String, Object> redisTemplate = (RedisTemplate<String, Object>) SpringUtil.getBean("redisTemplate");

        redisTemplate.opsForHash().increment(Const.READ_SUM, "" + id , 1);

        if (redisTemplate.opsForValue().get(Const.READ_RECENT + id) == null) {
            redisTemplate.opsForValue().set(Const.READ_RECENT + id, 1, 7, TimeUnit.DAYS);
        } else {
            redisTemplate.opsForValue().increment(Const.READ_RECENT + id, 1);
        }
    }


    /**
     * 把用户信息放置在redis里
     * @param token
     * @param user
     */
    public static void setUserToCache(String token, User user, Long time) {

        RedisTemplate<String, Object> redisTemplate = (RedisTemplate<String, Object>) SpringUtil.getBean("redisTemplate");

        HashMap<String, Object> map = new HashMap<>();
        map.put(Const.USER_OBJECT, user);
        map.put(Const.TOKEN, token);

        //续10分钟
        if (Boolean.FALSE.equals(redisTemplate.hasKey(Const.USER_PREFIX + user.getId()))) {
            redisTemplate.execute(new SessionCallback<>() {
                @Override
                public Object execute(@NonNull RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    operations.opsForHash().putAll(Const.USER_PREFIX + user.getId().toString(), map);
                    operations.expire(Const.USER_PREFIX + user.getId().toString(), time, TimeUnit.SECONDS);
                    operations.exec();
                    return null;
                }
            });
        }
    }


    /**
     * 将ES搜索完成的对象转化为mybatis-plus的page对象
     * @param hits 命中
     * @param currentPage 页码
     * @param pageSize 页型号
     * @param <T> 处理的类型
     * @return page对象
     */
    public static <T> Page<T>  hitsToPage(SearchHits<T> hits, Integer currentPage, Integer pageSize, long total) {
        ArrayList<T> list = new ArrayList<>();

        for (SearchHit<T> hit : hits.getSearchHits()) {
            list.add(hit.getContent());
        }

        Page<T> page = new Page<>(currentPage, pageSize);

        page.setRecords(list);
        page.setTotal(total);

        return page;
    }

    /**
     * 通知消息给MQ
     * @param id
     * @param message
     */
    public static void sendBlogMessageToMQ(Long id ,String message) {
        AmqpTemplate amqpTemplate = SpringUtil.getBean(AmqpTemplate.class);

        amqpTemplate.convertAndSend(
                RabbitConfig.ES_EXCHANGE,
                RabbitConfig.ES_BINDING_KEY,
                new PostMQIndexMessage(id, message));
    }

    /**
     * 删除缓存热点
     */
    public static void deleteHot(String... keys) {
        RedisTemplate<String, Object> redisTemplate = (RedisTemplate<String, Object>) SpringUtil.getBean("redisTemplate");

        for (String str : keys) {
            Set<String> targets = redisTemplate.keys(str);
            if (targets != null) {
                redisTemplate.delete(targets);
            }
        }

    }


    /**
     * 取得阅读量数据，数据在redis中
     * @param page
     */
    public static void setRead(Page<Blog> page) {

        RedisTemplate<String, Object> redisTemplate = (RedisTemplate<String, Object>) SpringUtil.getBean("redisTemplate");

        List<Blog> blogs = page.getRecords();
        ArrayList<Object> ids = new ArrayList<>();

        for(Blog blog : blogs) {
            ids.add(blog.getId().toString());
        }

        //为数据设置7日阅读和总阅读数
        List<Object> listSum = redisTemplate.opsForHash().multiGet(Const.READ_SUM, ids);
        for (int i = 0; i < blogs.size(); i++) {
            Blog blog = blogs.get(i);
            if (listSum.get(i) != null) {
                blog.setReadSum((Integer) listSum.get(i));
                Integer recentNum = (Integer) redisTemplate.opsForValue().get(Const.READ_RECENT + blog.getId());
                blog.setReadRecent(Objects.requireNonNullElse(recentNum, 0));
            } else {
                blog.setReadSum(0);
                blog.setReadRecent(0);
            }
        }

        page.setRecords(blogs);

    }


    /**
     * 将map类型的对象转化为实体类
     * @param json
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> T jsonToObj(Object json, Class<T> clazz) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.convertValue(json, clazz);
    }


    /**
     * 将list集合转化为page对象
     * https://blog.csdn.net/Jane_lavor/article/details/112788865
     * @param list 对象的集合
     * @param currentPage 当前第几页
     * @param size 一页多少条
     * @param <T> 对象所处类
     * @return page对象
     */
    public static <T> Page<T> listToPage(List<T> list, int currentPage, int size) {

        Page<T> page = new Page<>(currentPage, size);

        int start = (int) ((currentPage - 1) * page.getSize());
        int end = (start + page.getSize()) > list.size() ? list.size() : (int) (page.getSize() * currentPage);

        page.setRecords(list.subList(start, end));
        page.setTotal(list.size());

        return page;
    }

    /**
     * 删除图片文件夹
     * @param file
     */
    public static void deleteAllImg(File file) {
        if (file.exists()) {
            if (file.isFile()) {
                boolean b = file.delete();
            } else if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        deleteAllImg(f);
                    }
                }
            }
            boolean b = file.delete();
        } else {
            try {
                throw new FileNotFoundException("所删除的" + file.getAbsolutePath() + "不存在！");
            } catch (FileNotFoundException ignored) {
            }
        }
    }

    public static BlogPostDocument blogToDocument(Blog blog) {
        BlogPostDocument blogPostDocument = new BlogPostDocument();
        BeanUtil.copyProperties(blog, blogPostDocument, "username", "readSum", "readRecent", "created");

        //ES中保存的时间是格林尼治标准时间，如果直接存入ES，用kibana分析的时候会自动加8小时
        blogPostDocument.setCreated(blog.getCreated().minusHours(Const.GMT_PLUS_8));
        return blogPostDocument;
    }

    public static void documentToBlog(SearchHit<BlogPostDocument> hit, Blog blog) {
        UserService userService = SpringUtil.getBean(UserService.class);

        BeanUtil.copyProperties(hit.getContent(), blog, "created");

        blog.setCreated(hit.getContent().getCreated().plusHours(Const.GMT_PLUS_8));
        String username = userService.getOne(new QueryWrapper<User>().eq("id", hit.getContent().getUserId())).getUsername();
        blog.setUsername(username);
    }

    public static void initBlog(Blog blog) {
        blog.setUserId(ShiroUtil.getProfile().getId());
        blog.setCreated(LocalDateTime.now());
        blog.setStatus(0);
        blog.setTitle("每天都有好心情");
        blog.setDescription("");
        blog.setContent("");
        blog.setLink("");
    }
}
