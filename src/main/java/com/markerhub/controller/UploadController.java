package com.markerhub.controller;

import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.service.BlogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.elasticsearch.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 图片上传到img文件夹和删除
 * @author mingchiuli
 * @create 2021-11-05 5:02 PM
 */
@RestController
@Slf4j
public class UploadController {

    @Value("${uploadPath}")
    private String baseFolderPath;

    @Value("${imgFoldName}")
    private String img;

    BlogService blogService;

    @Autowired
    private void setBlogServiceImpl(BlogService blogService) {
        this.blogService = blogService;
    }

    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    @PostMapping("/upload")
    public Result upload(@RequestParam MultipartFile image, HttpServletRequest request, @RequestParam String created) {
        if (image != null) {

            String filePath;

            filePath = created.replaceAll("-", "")
                    .replaceAll(" ", "")
                    .replaceAll(":", "")
                    .replaceAll("T", "");

            File baseFolder = new File(baseFolderPath + img + "/" + filePath);

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
                e.printStackTrace();
                return Result.fail("上传失败");
            }
            return Result.succ(url.toString());
        }
        return Result.fail("上传失败");
    }


    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    @DeleteMapping("/delfile")
    public Result deleteFile(@RequestParam String url) {
        //常量是有关url的
        int index = url.indexOf(Const.UPLOAD_IMG_PATH) + Const.UPLOAD_IMG_PATH.length() - 1;
        String dest = url.substring(index);
        //配置文件里的是上传服务器的路径
        String finalDest = baseFolderPath + img + dest;
        File file = new File(finalDest);
        if (file.exists()) {
            boolean b = file.delete();

            log.info("删除rui:{}上传图片的结果:{}", url, b);

            return Result.succ("删除结果：" + b);
        }
        return Result.fail("文件不存在");
    }
}
