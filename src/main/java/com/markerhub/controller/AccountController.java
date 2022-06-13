package com.markerhub.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.map.MapUtil;
import com.google.code.kaptcha.Producer;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 登录相关
 */
@Slf4j
@RestController
public class AccountController {

    Producer producer;

    @Autowired
    public void setProducer(Producer producer) {
        this.producer = producer;
    }

    UserService userService;

    @Autowired
    private void setUserServiceImpl(UserService userService) {
        this.userService = userService;
    }

    JwtUtil jwtUtil;

    @Autowired
    private void setJwtUtils(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private void setRedisTemplateImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    @GetMapping("/captcha")
    public Result captcha() throws IOException {

        String key = UUID.randomUUID().toString();
        String code = producer.createText();

        BufferedImage image = producer.createImage(code);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);

        Base64.Encoder encoder = Base64.getEncoder();
        String str = "data:image/jpeg;base64,";

        String base64Img = str + encoder.encodeToString(outputStream.toByteArray());

        redisTemplate.opsForValue().set(Const.CAPTCHA_KEY + key, code, 120, TimeUnit.SECONDS);

        return Result.succ(
                MapUtil.builder()
                        .put(Const.TOKEN, key)
                        .put("captchaImg", base64Img)
                        .build()
        );
    }

}
