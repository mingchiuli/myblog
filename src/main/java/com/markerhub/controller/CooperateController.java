package com.markerhub.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.exception.AuthenticationException;
import com.markerhub.common.vo.CoNumberList;
import com.markerhub.common.vo.Content;
import com.markerhub.common.vo.Message;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.entity.*;
import com.markerhub.service.BlogService;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import com.markerhub.utils.MyUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
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

    JwtUtils jwtUtils;

    @Autowired
    public void setJwtUtils(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @MessageMapping("/pushUser/{blogId}")
    public void pushUser(@DestinationVariable Long blogId) {

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        ArrayList<UserEntityVo> users = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {

            UserEntityVo value = MyUtils.jsonToObj(entry.getValue(), UserEntityVo.class);
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
            Claims claim = jwtUtils.getClaimByToken(token);
            String username = claim.getSubject();

            String userId = userService.getOne(new QueryWrapper<UserEntity>().select("id").eq("username", username)).getId().toString();

            redisTemplate.opsForHash().delete(Const.CO_PREFIX + blogId, userId);

            redisTemplate.opsForHash().delete(Const.CO_NUM_PREFIX + blogId, userId);

            Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

            ArrayList<UserEntityVo> users = new ArrayList<>();

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {

                UserEntityVo value = MyUtils.jsonToObj(entry.getValue(), UserEntityVo.class);
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
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    @ResponseBody
    public Result coStatus(@PathVariable Long blogId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        CoNumberList number = new CoNumberList();

        number.setIndex0(Boolean.FALSE);
        number.setIndex1(Boolean.FALSE);
        number.setIndex2(Boolean.FALSE);

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {

            UserEntityVo user = MyUtils.jsonToObj(entry.getValue(), UserEntityVo.class);

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
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    @ResponseBody
    public Result init(@PathVariable Long blogId, @PathVariable Integer coNumber, HttpServletRequest request) {

        if (redisTemplate.opsForHash().size(Const.CO_PREFIX + blogId) >= 3) {
            throw new AuthenticationException("编辑室" + blogId + "已满");
        }

        String jwt = request.getHeader("Authorization");

        Claims claim = jwtUtils.getClaimByToken(jwt);
        String username = claim.getSubject();

        String userId = userService.getOne(new QueryWrapper<UserEntity>().select("id").eq("username", username)).getId().toString();


        BlogEntity blog = blogService.getById(blogId);

        Assert.notNull(blog, "该博客不存在");

        if (!Const.ADMIN.equals(userService.getOne(new QueryWrapper<UserEntity>().select("role").eq("id", userId)).getRole()) && blog.getStatus() == 1) {
            throw new AuthenticationException("游客账号没有编辑权限");
        }

        UserEntity user = userService.getBaseMapper().selectOne(new QueryWrapper<UserEntity>().eq("id", userId).select("id", "username", "avatar", "role"));
        UserEntityVo userVo = new UserEntityVo();
        BeanUtils.copyProperties(user, userVo);

        userVo.setNumber(coNumber);

        if (!redisTemplate.opsForHash().hasKey(Const.CO_PREFIX + blogId, userId)) {
            redisTemplate.opsForHash().put(Const.CO_PREFIX + blogId, userId, userVo);
        }

        redisTemplate.expire(Const.CO_PREFIX + blogId, 6 * 60, TimeUnit.MINUTES);

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        ArrayList<UserEntityVo> users = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {

            UserEntityVo value = MyUtils.jsonToObj(entry.getValue(), UserEntityVo.class);

            users.add(value);
        }

        users.sort(Comparator.comparingInt(UserEntityVo::getNumber));

        simpMessagingTemplate.convertAndSendToUser(blogId.toString(),"/topic/users", users);

        log.info("{}号用户加入{}号编辑室", userId, blogId);

        HashMap<String, Object> map = new HashMap<>();
        map.put("blog", blog);
        map.put("users", users);

        return Result.succ(map);
    }


}
