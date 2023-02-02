package com.markerhub.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.common.vo.BlogEntityVo;
import com.markerhub.entity.BlogEntity;
import com.markerhub.entity.UserEntity;
import com.markerhub.search.model.BlogPostDocument;
import com.markerhub.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.SneakyThrows;
import org.springframework.beans.BeanUtils;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;


/**
 * @author mingchiuli
 * @create 2021-11-02 8:55 PM
 */
public class MyUtils {

    public static Long reqToUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");

        JwtUtils jwtUtils = SpringUtils.getBean(JwtUtils.class);

        if(!StringUtils.hasLength(token)) {
            return (long) -1;
        } else {
            // 教验jwt
            Claims claim = jwtUtils.getClaimByToken(token);
            String username = Objects.requireNonNull(claim).getSubject();
            UserService userService = SpringUtils.getBean(UserService.class);
            UserEntity user = userService.getOne(new QueryWrapper<UserEntity>().select("id").eq("username", username));
            return user.getId();
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
        hits.getSearchHits().forEach(hit -> list.add(hit.getContent()));

        Page<T> page = new Page<>(currentPage, pageSize);

        page.setRecords(list);
        page.setTotal(total);

        return page;
    }

    /**
     * 针对ES对象的使用，把得分加进去
     */
    @SneakyThrows
    public static <T, E> Page<E>  hitsToPage(SearchHits<T> hits, Class<E> kClass, Integer currentPage, Integer pageSize, long total) {
        ArrayList<E> list = new ArrayList<>();

        for (SearchHit<T> hit : hits.getSearchHits()) {
            E instance = kClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(hit.getContent(), instance);
            Map<String, List<String>> highlightFields = hit.getHighlightFields();
            StringBuilder sb = new StringBuilder();
            highlightFields.forEach((key, value) -> sb.append(value));
            Method setMethod =  instance.getClass().getMethod("setScore", Float.class);
            setMethod.invoke(instance, hit.getScore());
            Method setHighlight =  instance.getClass().getMethod("setHighlight", String.class);
            setHighlight.invoke(instance, sb.toString());
            list.add(instance);
        }

        Page<E> page = new Page<>(currentPage, pageSize);

        page.setRecords(list);
        page.setTotal(total);
        return page;
    }


    /**
     * 取得阅读量数据，数据在redis中
     * @param page
     */
    public static void setRead(Page<BlogEntityVo> page) {

        RedisTemplate<String, Object> redisTemplate = SpringUtils.getBean(RedisTemplate.class);
        List<BlogEntityVo> blogs = page.getRecords();
//        ArrayList<Object> ids = new ArrayList<>();
//
//        for(BlogEntityVo blog : blogs) {
//            ids.add(blog.getId().toString());
//        }

        //为数据设置7日阅读和总阅读数
//        List<Object> listSum = redisTemplate.opsForHash().multiGet(Const.READ_SUM, ids);
        for (BlogEntityVo blog : blogs) {
            Integer recentNum = (Integer) redisTemplate.opsForValue().get(Const.READ_RECENT + blog.getId());
            blog.setReadRecent(Objects.requireNonNullElse(recentNum, 0));
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
        ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);
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

    public static BlogPostDocument blogToDocument(BlogEntity blog) {
        BlogPostDocument blogPostDocument = new BlogPostDocument();
        BeanUtils.copyProperties(blog, blogPostDocument,  "readCount", "created");

        //ES中保存的时间是格林尼治标准时间，如果直接存入ES，用kibana分析的时候会自动加8小时
        blogPostDocument.setCreated(ZonedDateTime.of(blog.getCreated(), ZoneId.of("Asia/Shanghai")));
        return blogPostDocument;
    }

    public static void documentToBlog(SearchHit<BlogPostDocument> hit, BlogEntityVo blog) {
        UserService userService = SpringUtils.getBean(UserService.class);

        BeanUtils.copyProperties(hit.getContent(), blog, "created");

        blog.setCreated(hit.getContent().getCreated().toLocalDateTime());
        String username = userService.getOne(new QueryWrapper<UserEntity>().select("username").eq("id", hit.getContent().getUserId())).getUsername();
        blog.setUsername(username);
    }

    public static void initBlog(BlogEntity blog) {
        UserService userService = SpringUtils.getBean(UserService.class);
        UserEntity user = userService.getOne(new QueryWrapper<UserEntity>().select("id").eq("username", SecurityContextHolder.getContext().getAuthentication().getName()));
        blog.setUserId(user.getId());
        blog.setCreated(LocalDateTime.now());
        blog.setStatus(0);
        blog.setReadCount(0L);
        blog.setTitle("每天都有好心情");
        blog.setDescription("");
        blog.setContent("");
        blog.setLink("");
    }
}
