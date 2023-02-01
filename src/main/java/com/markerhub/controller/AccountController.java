package com.markerhub.controller;

import com.google.code.kaptcha.Producer;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录相关
 */
@Slf4j
@RestController
public class AccountController {

    Producer producer;

    UserService userService;

    JwtUtils jwtUtils;

    RedisTemplate<String, Object> redisTemplate;

    public AccountController(Producer producer, UserService userService, JwtUtils jwtUtils, RedisTemplate<String, Object> redisTemplate) {
        this.producer = producer;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
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

        HashMap<String, String> map = new HashMap<>();

        map.put(Const.TOKEN, key);
        map.put("captchaImg", base64Img);

        return Result.succ(map);
    }

}
