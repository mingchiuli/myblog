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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author mingchiuli
 * @create 2022-06-06 3:50 PM
 */
@Configuration
@EnableScheduling
@Slf4j
public class ScheduledTask {

    ThreadPoolExecutor executor;

    @Autowired
    public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
        this.executor = threadPoolExecutor;
    }


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

    @Scheduled(cron = "0 0 0/2 * * ?")
    public void configureTask() {
        int[] years = blogService.searchYears();

        CompletableFuture<Void> var1 = CompletableFuture.runAsync(() -> {
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


                redisTemplate.opsForValue().set(contentPrefix, Result.succ(blog), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
                redisTemplate.opsForValue().set(statusPrefix, Result.succ(blog.getStatus()), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
                //bloomFilter
                redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_BLOG, blog.getId(), true);

            });
        }, executor);

        CompletableFuture<Void> var2 = CompletableFuture.runAsync(() -> {
            //list接口
            long count = blogService.count();
            long totalPage = count % Const.PAGE_SIZE == 0 ? count / Const.PAGE_SIZE : count / Const.PAGE_SIZE + 1;

            for (int i = 1; i <= totalPage; i++) {
                Page<Blog> page = new Page<>(i, Const.PAGE_SIZE);
                page = blogService.page(page, new QueryWrapper<Blog>().select("id", "title", "description", "link", "created").orderByDesc("created"));
                StringBuilder sb = new StringBuilder();
                try {
                    sb.append(objectMapper.writeValueAsString(i));
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage());
                }
                sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
                String pagesPrefix = Const.HOT_BLOGS + "::BlogController::list::" + sb;
                redisTemplate.opsForValue().set(pagesPrefix, Result.succ(page), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
                //bloomFilter
                redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_PAGE, i, true);
            }
        }, executor);

        CompletableFuture<Void> var3 = CompletableFuture.runAsync(() -> {
            //getCountByYear接口
            for (int year : years) {
                Integer countYear = blogService.getYearCount(year);
                StringBuilder sb = new StringBuilder();
                try {
                    sb.append(objectMapper.writeValueAsString(year));
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage());
                }
                sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
                String yearCountPrefix = Const.HOT_BLOGS + "::BlogController::getCountByYear::" + sb;
                redisTemplate.opsForValue().set(yearCountPrefix, Result.succ(countYear), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
            }
        }, executor);

        CompletableFuture<Void> var4 = CompletableFuture.runAsync(() -> {
            //listByYear接口
            for (int year : years) {

                //当前年份的总页数
                LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
                LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);
                long pageNum = blogService.count(new QueryWrapper<Blog>().between("created", start, end));
                for (int i = 1; i <= pageNum; i++) {
                    //每一页的缓存
                    Page<Blog> pageData = blogService.listByYear(i, year);
                    StringBuilder sb = new StringBuilder();
                    try {
                        sb.append(objectMapper.writeValueAsString(i));
                        sb.append(objectMapper.writeValueAsString(year));
                    } catch (JsonProcessingException e) {
                        log.error(e.getMessage());
                    }
                    sb = new StringBuilder(Arrays.toString(DigestUtil.md5(sb.toString())));
                    String yearListPrefix = Const.HOT_BLOGS + "::BlogController::listByYear::" + sb;
                    redisTemplate.opsForValue().set(yearListPrefix, Result.succ(pageData), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);

                    //bloom过滤器
                    redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_PAGE + year, i, true);
                }
            }
        }, executor);


        CompletableFuture<Void> var5 = CompletableFuture.runAsync(() -> {
            int[] ints = blogService.searchYears();
            String yearKey = Const.YEARS + "::BlogController::searchYears::";
            redisTemplate.opsForValue().set(yearKey, Result.succ(ints), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
        }, executor);

        CompletableFuture.allOf(var1, var2, var3, var4, var5);

        log.info("定时任务执行完毕");
    }
}
