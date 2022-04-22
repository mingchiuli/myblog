package com.markerhub.controller;

import cn.hutool.core.map.MapUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.vo.CoNumberList;
import com.markerhub.common.vo.Content;
import com.markerhub.common.vo.Message;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.*;
import com.markerhub.service.BlogService;
import com.markerhub.service.UserService;
import com.markerhub.shiro.JwtToken;
import com.markerhub.util.JwtUtil;
import com.markerhub.util.MyUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * convertAndSendToUser和convertAndSend调用的是同样的方法，最终只是增加了推送地址的前缀/user/{id}
 * @author mingchiuli
 * @create 2021-12-27 7:03 PM
 */
@Controller
@Slf4j
public class CooperateController {

    BlogService blogService;

    @Autowired
    public void setBlogService(BlogService blogService) {
        this.blogService = blogService;
    }

    UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    JwtUtil jwtUtil;

    @Autowired
    public void setJwtUtils(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @MessageMapping("/pushUser/{blogId}")
    public void pushUser(@DestinationVariable Long blogId) {

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        ArrayList<User> users = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {

            User value = MyUtil.jsonToObj(entry.getValue(), User.class);
            users.add(value);
        }

        simpMessagingTemplate.convertAndSendToUser(blogId.toString(),"/topic/users", users);
    }


    @MessageMapping("/destroy/{blogId}")
    public void destroy(@Headers Map<String, Object> headers, @DestinationVariable Long blogId) {
        LinkedMultiValueMap<String, String> map = (LinkedMultiValueMap<String, String>) headers.get("nativeHeaders");
        List<String> authorization = map.get("Authorization");
        if (authorization != null) {
            String token = authorization.get(0);

            JwtToken jwtToken = new JwtToken(token);
            Claims claim = jwtUtil.getClaimByToken((String) jwtToken.getCredentials());
            String userId = claim.getSubject();

            redisTemplate.opsForHash().delete(Const.CO_PREFIX + blogId, userId);

            redisTemplate.opsForHash().delete(Const.CO_NUM_PREFIX + blogId, userId);

            Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

            ArrayList<User> users = new ArrayList<>();

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {

                User value = MyUtil.jsonToObj(entry.getValue(), User.class);
                users.add(value);
            }

            simpMessagingTemplate.convertAndSendToUser(blogId.toString(),"/topic/popUser", users);

            log.info("{}号用户退出{}号编辑室", userId, blogId);

        }
    }

    @MessageMapping("/chat/{from}/{to}")
    public void chat(String msg, @DestinationVariable String from, @DestinationVariable Long to) {

        Message message = new Message();

        message.setMessage(msg);

        message.setFrom(from);

        message.setTo(to);

        simpMessagingTemplate.convertAndSendToUser(to.toString(), "/queue/chat", message);

    }


    @MessageMapping("/sync/{from}")
    public void syncContent(@DestinationVariable Long from, String content) {
        Content msg = new Content();

        msg.setContent(content);

        msg.setFrom(from);

        simpMessagingTemplate.convertAndSend("/topic/content", msg);
    }


    @MessageMapping("/taskOver/{from}")
    public void taskOver(@DestinationVariable Long from) {

        simpMessagingTemplate.convertAndSend("/topic/over", from);
    }

    @GetMapping("/coStatus/{blogId}")
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    @ResponseBody
    public Result coStatus(@PathVariable Long blogId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        CoNumberList number = new CoNumberList();

        number.setIndex0(Boolean.FALSE);
        number.setIndex1(Boolean.FALSE);
        number.setIndex2(Boolean.FALSE);

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {

            User user = MyUtil.jsonToObj(entry.getValue(), User.class);

            if (user.getNumber() == 0) {
                number.setIndex0(Boolean.TRUE);
                return Result.succ(number);
            } else if (user.getNumber() == 1) {
                number.setIndex1(Boolean.TRUE);
                return Result.succ(number);
            } else if (user.getNumber() == 2){
                number.setIndex2(Boolean.TRUE);
                return Result.succ(number);
            }
        }

        return Result.succ(number);
    }


    @GetMapping("/blogWSCooperate/{blogId}/{coNumber}")
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    @ResponseBody
    public Result init(@PathVariable Long blogId, @PathVariable Integer coNumber, HttpServletRequest request) {

        if (redisTemplate.opsForHash().size(Const.CO_PREFIX + blogId) >= 3) {
            throw new AuthenticationException("编辑室" + blogId + "已满");
        }

        String jwt = request.getHeader("Authorization");

        JwtToken jwtToken = new JwtToken(jwt);
        Claims claim = jwtUtil.getClaimByToken((String) jwtToken.getCredentials());
        String userId = claim.getSubject();


        Blog blog = blogService.getById(blogId);

        Assert.notNull(blog, "该博客不存在");

        if (!Const.ADMIN.equals(userService.getById(userId).getRole()) && blog.getStatus() == 1) {
            throw new AuthenticationException("游客账号没有编辑权限");
        }

        User user = userService.getBaseMapper().selectOne(new QueryWrapper<User>().eq("id", userId).select("id", "username", "avatar", "role"));

        user.setNumber(coNumber);

        if (!redisTemplate.opsForHash().hasKey(Const.CO_PREFIX + blogId, userId)) {
            redisTemplate.opsForHash().put(Const.CO_PREFIX + blogId, userId, user);
        }

        redisTemplate.expire(Const.CO_PREFIX + blogId, 6 * 60, TimeUnit.MINUTES);

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        ArrayList<User> users = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {

            User value = MyUtil.jsonToObj(entry.getValue(), User.class);

            users.add(value);
        }

        users.sort(Comparator.comparingInt(User::getNumber));

        simpMessagingTemplate.convertAndSendToUser(blogId.toString(),"/topic/users", users);

        log.info("{}号用户加入{}号编辑室", userId, blogId);

        return Result.succ(MapUtil.builder()
                .put("blog", blog)
                .put("users", users).map());
    }


}
