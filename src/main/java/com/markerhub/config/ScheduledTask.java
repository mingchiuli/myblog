package com.markerhub.config;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Blog;
import com.markerhub.service.BlogService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author mingchiuli
 * @create 2022-06-06 3:50 PM
 */
@Configuration
@EnableScheduling
@Slf4j
public class ScheduledTask {

    @Value("${year}")
    private String yearsStr;

    BlogService blogService;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    RedisTemplate<String, Object> redisTemplate;

    ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SneakyThrows
    @Scheduled(cron = "0 0/1 * * * ?")
    public void configureTask() {
        //detail和getBlogStatus接口
        List<Blog> blogs = blogService.list(new QueryWrapper<Blog>().ne("status", 1));
        blogs.forEach(blog -> {
            StringBuilder builder = new StringBuilder();

            try {
                builder.append(objectMapper.writeValueAsString(blog.getId()));
            } catch (JsonProcessingException e) {
                log.info(e.getMessage());
            }

            builder = new StringBuilder(Arrays.toString(DigestUtil.md5(builder.toString())));
            String contentPrefix = Const.HOT_BLOG + "::BlogController::detail::" + builder;
            String statusPrefix = Const.BLOG_STATUS + "::BlogController::getBlogStatus::" + builder;

            redisTemplate.opsForValue().set(contentPrefix, Result.succ(blog), new Random().nextInt(120) + 1, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(statusPrefix, Result.succ(blog.getStatus()), new Random().nextInt(120) + 1, TimeUnit.MINUTES);
            //bloomFilter
            redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_BLOG, blog.getId(), true);

        });

        //list接口
        long count = blogService.count();
        long totalPage = count % Const.PAGE_SIZE == 0 ? count / Const.PAGE_SIZE : count / Const.PAGE_SIZE + 1;

        for (int i = 1; i <= totalPage; i++) {
            Page<Blog> page = new Page<>(i, Const.PAGE_SIZE);
            page = blogService.page(page, new QueryWrapper<Blog>().select("id", "title", "description", "link", "created").orderByDesc("created"));
            StringBuilder sb = new StringBuilder();
            sb.append(objectMapper.writeValueAsString(i));
            sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
            String pagesPrefix = Const.HOT_BLOGS + "::BlogController::list::" + sb;
            redisTemplate.opsForValue().set(pagesPrefix, Result.succ(page), new Random().nextInt(120) + 1, TimeUnit.MINUTES);
            //bloomFilter
            redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_PAGE, i, true);
        }

        //getCountByYear接口
        String[] years = yearsStr.split(",");

        for (String yearStr : years) {
            Integer year = Integer.parseInt(yearStr);
            Integer countYear = blogService.getYearCount(year);
            StringBuilder sb = new StringBuilder();
            sb.append(objectMapper.writeValueAsString(year));
            sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
            String yearCountPrefix = Const.HOT_BLOGS + "::BlogController::getCountByYear::" + sb;
            redisTemplate.opsForValue().set(yearCountPrefix, Result.succ(countYear), new Random().nextInt(120) + 1, TimeUnit.MINUTES);
        }

        //listByYear接口
        for (String yearStr : years) {
            int year = Integer.parseInt(yearStr);

            //当前年份的总页数
            LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
            LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);
            long pageNum = blogService.count(new QueryWrapper<Blog>().between("created", start, end));
            for (int i = 1; i <= pageNum; i++) {
                //每一页的缓存
                Page<Blog> pageData = blogService.listByYear(i, year);
                StringBuilder sb = new StringBuilder();
                sb.append(objectMapper.writeValueAsString(i));
                sb.append(objectMapper.writeValueAsString(year));
                sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
                String yearListPrefix = Const.HOT_BLOGS + "::BlogController::listByYear::" + sb;
                redisTemplate.opsForValue().set(yearListPrefix, Result.succ(pageData), new Random().nextInt(120) + 1, TimeUnit.MINUTES);

                //bloom过滤器
                redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_PAGE + year, i, true);
            }
        }

        log.info("定时任务执行完毕");
    }
}
