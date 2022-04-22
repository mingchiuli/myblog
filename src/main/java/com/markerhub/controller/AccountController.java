package com.markerhub.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.map.MapUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.google.code.kaptcha.Producer;
import com.markerhub.common.dto.LoginDto;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.User;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtil;
import com.markerhub.util.MyUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import javax.imageio.ImageIO;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
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


    @PostMapping("/login")
    @Transactional
    public Result login(@Validated @RequestBody LoginDto loginDto, HttpServletResponse response) {

       String code = loginDto.getCode();
       String key = loginDto.getToken();

        if (StringUtils.isBlank(code) || StringUtils.isBlank(key)) {
            return Result.fail("验证码无效");
        }

        if (!code.equals(redisTemplate.opsForValue().get(Const.CAPTCHA_KEY + key))) {
            return Result.fail("验证码错误");
        }

        redisTemplate.delete(Const.CAPTCHA_KEY + key);

        User user = userService.getOne(new QueryWrapper<User>().eq("username", loginDto.getUsername()));
        Assert.notNull(user, "用户不存在");


        if (user.getStatus() == 1) {
            return Result.fail("账号已停用");
        }

        if(!user.getPassword().equals(SecureUtil.md5(loginDto.getPassword()))){
            return Result.fail("密码不正确");
        }

        String jwt = jwtUtil.generateToken(user.getId());

        if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.USER_PREFIX + user.getId())) && user.getStatus() == 0) {
             return Result.fail("用户已登录");
        }

        response.setHeader("Authorization", jwt);
        response.setHeader("Access-control-Expose-Headers", "Authorization");

        MyUtil.setUserToCache(jwt, user, (long) (15 * 60));

        userService.update(new UpdateWrapper<User>().set("last_login", LocalDateTime.now()).eq("id", user.getId()));

        log.info("{}号用户登录完成", user.getId());

        return Result.succ(MapUtil.builder()
                .put("id", user.getId())
                .put("username", user.getUsername())
                .put("avatar", user.getAvatar())
                .put("email", user.getEmail())
                .put("role", user.getRole())
                .map()
        );
    }

    @RequiresAuthentication
    @GetMapping("/logout")
    public Result logout(ServletRequest servletRequest) {

        //进行常规登出操作
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String token = request.getHeader("Authorization");

        Claims claim = jwtUtil.getClaimByToken(token);
        String userId = claim.getSubject();

        Boolean delete = redisTemplate.delete(Const.USER_PREFIX + userId);
        Assert.isTrue(Boolean.TRUE.equals(delete), "退出登录失败");

        SecurityUtils.getSubject().logout();

        log.info("{}号用户退出登录", userId);

        return Result.succ(null);
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
