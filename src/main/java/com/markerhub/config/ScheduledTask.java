package com.markerhub.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.BlogEntity;
import com.markerhub.service.BlogService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
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
    @Qualifier("scheduledThreadPoolExecutor")
    public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
        this.executor = threadPoolExecutor;
    }


    BlogService blogService;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SneakyThrows
    @Scheduled(cron = "0 0 0/2 * * ?")
    public void configureTask() {
        int[] years = blogService.searchYears();

        CompletableFuture<Void> var1 = CompletableFuture.runAsync(() -> {
            //detail和getBlogStatus接口
            List<BlogEntity> blogs = blogService.list(new QueryWrapper<BlogEntity>().ne("status", 1));
            blogs.forEach(blog -> {
                StringBuilder builder = new StringBuilder();

                builder.append("::");
                builder.append(blog.getId());

                String contentPrefix = Const.HOT_BLOG + "::BlogServiceImpl::getBlogDetail" + builder;
                String statusPrefix = Const.BLOG_STATUS + "::BlogController::getBlogStatus" + builder;


                redisTemplate.opsForValue().set(contentPrefix, blog, ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
                redisTemplate.opsForValue().set(statusPrefix, Result.succ(blog.getStatus()), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);

            });

            //bloomFilter
            List<BlogEntity> blogIds = blogService.list(new QueryWrapper<BlogEntity>().select("id"));
            blogIds.forEach(blogId -> {
                redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_BLOG, blogId.getId(), true);
            });



        }, executor);

        CompletableFuture<Void> var2 = CompletableFuture.runAsync(() -> {
            //list接口
            long count = blogService.count();
            long totalPage = count % Const.PAGE_SIZE == 0 ? count / Const.PAGE_SIZE : count / Const.PAGE_SIZE + 1;

            for (int i = 1; i <= totalPage; i++) {
                Page<BlogEntity> page = new Page<>(i, Const.PAGE_SIZE);
                page = blogService.page(page, new QueryWrapper<BlogEntity>().select("id", "title", "description", "link", "created").orderByDesc("created"));
                String sb = "::" + i;
                String pagesPrefix = Const.HOT_BLOGS + "::BlogController::list" + sb;
                redisTemplate.opsForValue().set(pagesPrefix, Result.succ(page), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
                //bloomFilter
                redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_PAGE, i, true);
            }
        }, executor);

        CompletableFuture<Void> var3 = CompletableFuture.runAsync(() -> {
            //getCountByYear接口
            for (int year : years) {
                Integer countYear = blogService.getYearCount(year);
                String sb = "::" + year;
                String yearCountPrefix = Const.HOT_BLOGS + "::BlogController::getCountByYear" + sb;
                redisTemplate.opsForValue().set(yearCountPrefix, Result.succ(countYear), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
            }
        }, executor);

        CompletableFuture<Void> var4 = CompletableFuture.runAsync(() -> {
            //listByYear接口
            for (int year : years) {

                //当前年份的总页数
                LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
                LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);
                long pageNum = blogService.count(new QueryWrapper<BlogEntity>().between("created", start, end));
                for (int i = 1; i <= pageNum; i++) {
                    //每一页的缓存
                    Page<BlogEntity> pageData = blogService.listByYear(i, year);
                    String sb = "::" + i + "::" + year;
                    String yearListPrefix = Const.HOT_BLOGS + "::BlogController::listByYear" + sb;
                    redisTemplate.opsForValue().set(yearListPrefix, Result.succ(pageData), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
                    //bloom过滤器
                    redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_PAGE + year, i, true);
                }
            }
        }, executor);


        //searchYears和getCountByYear
        CompletableFuture<Void> var5 = CompletableFuture.runAsync(() -> {
            int[] ints = blogService.searchYears();
            String yearKey = Const.YEARS + "::BlogController::searchYears";
            redisTemplate.opsForValue().set(yearKey, Result.succ(ints), ThreadLocalRandom.current().nextInt(120) + 1, TimeUnit.MINUTES);
            //getCountByYear的bloom
            for (int year : ints) {
                redisTemplate.opsForValue().setBit(Const.BLOOM_FILTER_YEARS, year, true);
            }
        }, executor);

        CompletableFuture.allOf(var1, var2, var3, var4, var5).get();

        log.info("定时任务执行完毕");
    }
}
