package com.markerhub.controller;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.bloom.Bloom;
import com.markerhub.common.bloom.handler.impl.*;
import com.markerhub.common.cache.Cache;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.common.valid.BlogAuthentication;
import com.markerhub.common.valid.ListValue;
import com.markerhub.common.vo.BlogPostDocumentVo;
import com.markerhub.common.vo.BlogEntityVo;
import com.markerhub.entity.BlogEntity;
import com.markerhub.service.BlogService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p>
 * 前台博客操作
 * </p>
 *
 * @since 2020-05-25
 */
@Slf4j
@RestController
@Validated
public class BlogController {


    RedisTemplate<String, Object> redisTemplate;

    BlogService blogService;

    ThreadPoolExecutor executor;

    public BlogController(RedisTemplate<String, Object> redisTemplate,
                          BlogService blogService,
                          @Qualifier("imgUploadThreadPoolExecutor") ThreadPoolExecutor executor) {
        this.redisTemplate = redisTemplate;
        this.blogService = blogService;
        this.executor = executor;
    }

    @Value("${vueblog.oos.accessKeyId}")
    private String keyId;

    @Value("${vueblog.oos.accessKeySecret}")
    private String keySecret;

    @Value("${vueblog.oos.bucketName}")
    private String bucket;

    @Value("${vueblog.oos.endpoint}")
    private String ep;

    private static final String host = "https://bloglmc.oss-cn-hangzhou.aliyuncs.com";


    /**
     * 图片上传OOS
     *
     * @param image
     * @param request
     * @param created
     * @return
     */
    @Deprecated
    public Result upload(@RequestParam MultipartFile image, HttpServletRequest request, @RequestParam String created) {
        if (image != null) {

            String filePath;

            filePath = created.replaceAll("-", "")
                    .replaceAll(" ", "")
                    .replaceAll(":", "")
                    .replaceAll("T", "");

            File baseFolder = new File("baseFolderPath" + "img" + "/" + filePath);

            if (!baseFolder.exists()) {
                boolean b = baseFolder.mkdirs();

                log.info("上传{}时间的图片结果:{}", created, b);
            }

            StringBuilder url = new StringBuilder();
            String filename = image.getOriginalFilename();
            //https://blog.csdn.net/Cheguangquan/article/details/104121923

            if (filename == null) {
                throw new ResourceNotFoundException("图片上传出错");
            }

            String imgName = UUID.randomUUID().toString()
                    .replace("_", "")
                    + "_"
                    + filename
                    .replaceAll(" ", "");


            url.append(request.getScheme())
                    .append("://")
                    .append(request.getServerName())
                    .append(":")
                    .append(request.getServerPort())
                    .append(request.getContextPath())
                    .append(Const.UPLOAD_IMG_PATH)
                    .append(filePath)
                    .append("/")
                    .append(imgName);
            try {
                File dest = new File(baseFolder, imgName);
                FileCopyUtils.copy(image.getBytes(), dest);
            } catch (IOException e) {
                log.error(e.getMessage());
                return Result.fail("上传失败");
            }
            return Result.succ(url.toString());
        }
        return Result.fail("上传失败");
    }


    /**
     * 图片阿里云OSS
     *
     * @param image
     * @return
     */
    @SneakyThrows
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    @PostMapping("/upload")
    public Result uploadAliOSS(@RequestParam MultipartFile image) {
        if (image != null) {

            String username = UUID.randomUUID().toString();
            String originalFilename = image.getOriginalFilename();
            originalFilename = originalFilename.replace(" ", "");
            String objectName = username + "/" + originalFilename;
            byte[] imageBytes = image.getBytes();

            var ref = new Object() {
                OSS ossClient;
            };

            executor.execute(() -> {
                try {
                    // Endpoint以华东1（杭州）为例，其它Region请按实际情况填写。
                    String endpoint = ep;
                    // 阿里云账号AccessKey拥有所有API的访问权限，风险很高。强烈建议您创建并使用RAM用户进行API访问或日常运维，请登录RAM控制台创建RAM用户。
                    String accessKeyId = keyId;
                    String accessKeySecret = keySecret;
                    // 填写Bucket名称，例如examplebucket。
                    String bucketName = bucket;
                    // 填写Object完整路径，例如exampledir/exampleobject.txt。Object完整路径中不能包含Bucket名称。
                    // 创建OSSClient实例。
                    ref.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                    ref.ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(imageBytes));
                } catch (OSSException oe) {
                    log.info("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.");
                    log.info("Error Message:" + oe.getErrorMessage());
                    log.info("Error Code:" + oe.getErrorCode());
                    log.info("Request ID:" + oe.getRequestId());
                    log.info("Host ID:" + oe.getHostId());
                } catch (ClientException ce) {
                    log.info("Caught an ClientException, which means the client encountered "
                            + "a serious internal problem while trying to communicate with OSS, "
                            + "such as not being able to access the network.");
                    log.info("Error Message:" + ce.getMessage());
                } catch (Exception e) {
                    log.info(String.valueOf(e));
                } finally {
                    if (ref.ossClient != null) {
                        ref.ossClient.shutdown();
                    }
                }
            });

            //https://bloglmc.oss-cn-hangzhou.aliyuncs.com/admin/42166d224f4a20a45eca28b691529822730ed0ee.jpeg
            return Result.succ(host + "/" + objectName);
        }
        return Result.fail(null);
    }


    /**
     * 图片删除
     * @param url
     * @return
     */
    @Deprecated
    public Result deleteFile(@RequestParam String url) {
        //常量是有关url的
        int index = url.indexOf(Const.UPLOAD_IMG_PATH) + Const.UPLOAD_IMG_PATH.length() - 1;
        String dest = url.substring(index);
        //配置文件里的是上传服务器的路径
        String finalDest = "baseFolderPath" + "img" + dest;
        File file = new File(finalDest);
        if (file.exists()) {
            boolean b = file.delete();

            log.info("删除rui:{}上传图片的结果:{}", url, b);

            return Result.succ("删除结果：" + b);
        }
        return Result.fail("文件不存在");
    }

    /**
     * 图片删除
     * @param url
     * @return
     */
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    @DeleteMapping("/delfile")
    public Result deleteFileAliOos(@RequestParam String url) {

        String objectName = url.replace(host + "/", "");

        // Endpoint以华东1（杭州）为例，其它Region请按实际情况填写。
        String endpoint = ep;
        // 阿里云账号AccessKey拥有所有API的访问权限，风险很高。强烈建议您创建并使用RAM用户进行API访问或日常运维，请登录RAM控制台创建RAM用户。
        String accessKeyId = keyId;
        String accessKeySecret = keySecret;
        // 填写Bucket名称，例如examplebucket。
        String bucketName = bucket;
        // 填写Object完整路径，例如exampledir/exampleobject.txt。Object完整路径中不能包含Bucket名称。

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            ossClient.deleteObject(bucketName, objectName);
        } catch (OSSException oe) {
            log.info("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            log.info("Error Message:" + oe.getErrorMessage());
            log.info("Error Code:" + oe.getErrorCode());
            log.info("Request ID:" + oe.getRequestId());
            log.info("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            log.info("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            log.info("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
        return Result.succ(null);
    }


    /**
     * 按照年份给出博客内容
     * @param currentPage
     * @param year
     * @return
     */

    @GetMapping("/blogsByYear/{year}/{currentPage}")
    @Cache(name = Const.HOT_BLOGS)//缓存页面信息
    @Bloom(name = ListByYearHandler.class)
    public Result listByYear(@PathVariable(name = "currentPage") Integer currentPage, @PathVariable(name = "year") Integer year) {
        Page<BlogEntity> pageData = blogService.listByYear(currentPage, year);
        return Result.succ(pageData);
    }

    @GetMapping("/getCountByYear/{year}")
    @Cache(name = Const.HOT_BLOGS)
    @Bloom(name = GetCountByYearHandler.class)
    public Result getCountByYear(@PathVariable(name = "year") Integer year) {
        Integer count = blogService.getYearCount(year);
        return Result.succ(count);
    }

    /**
     * 所有博客内容
     * @param currentPage
     * @return
     */
    @Bloom(name = ListHandler.class)
    @Cache(name = Const.HOT_BLOGS)//缓存页面信息
    @GetMapping("/blogs/{currentPage}")
    public Result list(@PathVariable(name = "currentPage") Integer currentPage) {
        Page<BlogEntity> pageData = blogService.listBlogsByPage(currentPage);
        return Result.succ(pageData);
    }


    /**
     * 博客详情
     * @param id
     * @return
     */

    @GetMapping("/blog/{id}")
    @Bloom(name = DetailHandler.class)
    public Result detail(@PathVariable(name = "id") @BlogAuthentication Long id) {
        BlogEntity blog = blogService.getBlogDetail(id);
        blogService.setReadCount(id);
        return Result.succ(blog);
    }

    /**
     * @param id
     * @return
     */
    @GetMapping("/blogAuthorized/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result detailAuthorized(@PathVariable(name = "id") Long id) {
        BlogEntity blog = blogService.getAuthorizedBlogDetail(id);
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
        BlogEntity blog = blogService.getLockedBlog(blogId, token);
        if (blog == null) {
            return Result.fail("密钥错误");
        } else {
            return Result.succ(blog);
        }
    }


    /**
     * 搜索功能，从es搜索
     */
    @GetMapping("/search/{status}/{currentPage}")
    public Result search(@PathVariable Integer currentPage, @PathVariable @Validated @ListValue(values = {0, 1}, message = "必须提交0或1") Integer status , @RequestParam String keyword) {
        Page<BlogPostDocumentVo> page = blogService.selectBlogsByES(currentPage, keyword, status);
        return Result.succ(page);
    }


    @GetMapping("/searchByYear/{status}/{currentPage}/{year}")
    public Result searchByYear(@PathVariable Integer currentPage, @RequestParam String keyword, @PathVariable Integer year, @PathVariable @Validated @ListValue(values = {0, 1}, message = "必须提交0或1") Integer status) {
        Page<BlogPostDocumentVo> page = blogService.selectYearBlogsByES(currentPage, keyword, year, status);
        return Result.succ(page);
    }

    /**
     * 修改博客
     * @param blog
     * @return
     */
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    @PostMapping("/blog/edit")
    public Result edit(@Validated @RequestBody BlogEntityVo blog) {

        blogService.updateBlog(blog);

        return Result.succ(null);
    }

    /**
     * 初始化文章，目的是拿到创建时间，从而让每篇文章的上传图片位于每一个文件夹中
     * @return
     */
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    @GetMapping("/addNewBlog")
    public Result addNewBlog() {
        Long id = blogService.initBlog();
        return Result.succ(id);
    }


    /**
     * 查看已经删除的博客
     * @param currentPage
     * @return
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/queryDeletedBlogs")
    public Result listDeleted(@RequestParam String title, @RequestParam Integer currentPage, @RequestParam Integer size, @RequestParam Long userId) {
        Page<BlogEntityVo> page = blogService.selectDeletedBlogs(title, currentPage, size, userId);
        return Result.succ(page);

    }

    /**
     * 恢复删除的博客
     * @param id
     * @return
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/recoverBlogs/{id}/{userId}")
    public Result recoverBlog(@PathVariable(name = "id") Long id, @PathVariable(name = "userId") Long userId) {
        blogService.recoverBlog(id, userId);
        return Result.succ(null);
    }

    /**
     * 更改文章状态，0为公开，1为登录后可阅读
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/modifyBlogStatus/{id}/{status}")
    public Result modifyBlogStatus(@PathVariable Long id, @PathVariable Integer status) {
        blogService.changeBlogStatus(id, status);
        return Result.succ(null);
    }

    /**
     * 后台获取博客信息
     */
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl', 'guest')")
    @GetMapping("/getAllBlogs")
    public Result getAllBlogs(@RequestParam Integer currentPage, @RequestParam Integer size) {
        Page<BlogEntityVo> page = blogService.getAllBlogs(currentPage, size);
        return Result.succ(page);
    }


    /**
     * 查询博客，包含总阅读数和最近7日阅读数
     * @param currentPage
     * @return
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/queryBlogs")
    public Result queryBlogs(@RequestParam String keyword, @RequestParam Integer currentPage, @RequestParam Integer size) {
        Page<BlogEntityVo> page = blogService.queryBlogsAbstract(keyword, currentPage, size);
        return Result.succ(page);
    }


    /**
     * 删除博客
     * @param ids
     * @return
     */
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/deleteBlogs")
    public Result deleteBlogs(@RequestBody Long[] ids) {
        blogService.deleteBlogs(ids);
        return Result.succ(null);
    }


    /**
     * 设置阅读密钥
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/setBlogToken")
    public Result setBlogToken() {
        blogService.setBlogToken();
        return Result.succ(null);
    }

    /**
     * 获取阅读密钥
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/getBlogToken")
    public Result getBlogToken() {
        String token = blogService.getBlogToken();
        return Result.succ(token);
    }

    /**
     * 获取文章状态
     */
    @Bloom(name = GetBlogStatusHandler.class)
    @GetMapping("/blogStatus/{blogId}")
    @Cache(name = Const.BLOG_STATUS)
    public Result getBlogStatus(@PathVariable Long blogId) {
        Integer status = blogService.getOne(new QueryWrapper<BlogEntity>().select("status").eq("id", blogId)).getStatus();
        return Result.succ(status);
    }

    /**
     * 获取年份状态
     */
    @GetMapping("/searchYears")
    @Cache(name = Const.YEARS)
    public Result searchYears() {
        int[] years = blogService.searchYears();
        return Result.succ(years);
    }

}
