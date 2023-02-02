package com.markerhub.cooperate.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.valid.CooperateBlogId;
import com.markerhub.common.vo.CoNumberList;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.config.RabbitConfig;
import com.markerhub.cooperate.dto.Container;
import com.markerhub.entity.*;
import com.markerhub.service.BlogService;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import com.markerhub.utils.MyUtils;
import com.markerhub.cooperate.dto.impl.*;
import io.jsonwebtoken.Claims;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class CooperateHandler {

    RabbitTemplate rabbitTemplate;

    BlogService blogService;


    UserService userService;


    RedisTemplate<String, Object> redisTemplate;
    SimpMessagingTemplate simpMessagingTemplate;

    JwtUtils jwtUtils;

    public CooperateHandler(RabbitTemplate rabbitTemplate, BlogService blogService, UserService userService, RedisTemplate<String, Object> redisTemplate, SimpMessagingTemplate simpMessagingTemplate, JwtUtils jwtUtils) {
        this.rabbitTemplate = rabbitTemplate;
        this.blogService = blogService;
        this.userService = userService;
        this.redisTemplate = redisTemplate;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.jwtUtils = jwtUtils;
    }

    @MessageMapping("/pushUser/{blogId}")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    public void pushUser(@DestinationVariable Long blogId) {

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        ArrayList<UserEntityVo> users = new ArrayList<>();

        entries.forEach((k, v) -> {
            UserEntityVo value = MyUtils.jsonToObj(v, UserEntityVo.class);
            users.add(value);
        });

        String idStr = blogId.toString();
        PushUserDto dto = transferToDto(PushUserDto.Bind.class, PushUserDto.class,
                new Object[]{idStr, users}, new Class[]{idStr.getClass(), users.getClass()});

        rabbitTemplate.convertAndSend(
                RabbitConfig.WS_FANOUT_EXCHANGE,RabbitConfig.WS_BINDING_KEY + RabbitConfig.serverMark,
                dto);

    }


    @MessageMapping("/destroy/{blogId}")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
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
            entries.forEach((k, v) -> {
                UserEntityVo value = MyUtils.jsonToObj(v, UserEntityVo.class);
                users.add(value);
            });

            String idStr = blogId.toString();
            DestroyDto dto = transferToDto(DestroyDto.Bind.class, DestroyDto.class,
                    new Object[]{idStr, users}, new Class[]{idStr.getClass(), users.getClass()});

            rabbitTemplate.convertAndSend(
                    RabbitConfig.WS_FANOUT_EXCHANGE,RabbitConfig.WS_BINDING_KEY + RabbitConfig.serverMark,
                    dto);

            log.info("{}号用户退出{}号编辑室", userId, blogId);

        }
    }

    @MessageMapping("/chat/{from}/{to}/{blogId}")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    public void chat(String msg, @DestinationVariable String from, @DestinationVariable Long to, @DestinationVariable Long blogId) {
        UserEntityVo userEntityVo = MyUtils.jsonToObj(redisTemplate.opsForHash().get(Const.CO_PREFIX + blogId, to.toString()), UserEntityVo.class);
        if (userEntityVo != null) {
            String toServerIpHost = userEntityVo.getServerIpHost();
            String idStr = blogId.toString();
            String toStr = to.toString();
            ChatDto dto = transferToDto(ChatDto.Message.class, ChatDto.class, new Object[]{msg, from, toStr, idStr},
                    new Class[]{msg.getClass(), from.getClass(), toStr.getClass(), idStr.getClass()});
            rabbitTemplate.convertAndSend(
                    RabbitConfig.WS_TOPIC_EXCHANGE,RabbitConfig.WS_BINDING_KEY + toServerIpHost,
                    dto);
        }
    }


    @MessageMapping("/sync/{from}/{blogId}")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    public void syncContent(@DestinationVariable Long from, String content, @DestinationVariable Long blogId) {
        String fromStr = from.toString();
        String idStr = blogId.toString();
        SyncContentDto dto = transferToDto(SyncContentDto.Content.class, SyncContentDto.class,
                new Object[]{fromStr, content, idStr}, new Class[]{fromStr.getClass(), content.getClass(), idStr.getClass()});
        rabbitTemplate.convertAndSend(
                RabbitConfig.WS_FANOUT_EXCHANGE,RabbitConfig.WS_BINDING_KEY  + RabbitConfig.serverMark,
                dto);
    }


    @MessageMapping("/taskOver/{from}")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    public void taskOver(@DestinationVariable Long from) {
        String fromStr = from.toString();
        TaskOverDto dto = transferToDto(String.class, TaskOverDto.class, new Object[]{fromStr}, new Class[]{fromStr.getClass()});
        rabbitTemplate.convertAndSend(
                RabbitConfig.WS_FANOUT_EXCHANGE,RabbitConfig.WS_BINDING_KEY  + RabbitConfig.serverMark,
                dto);

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


        entries.forEach((k, v) -> {
            UserEntityVo user = MyUtils.jsonToObj(v, UserEntityVo.class);
            switch (user.getNumber()) {
                case 0 -> number.setIndex0(Boolean.TRUE);
                case 1 -> number.setIndex1(Boolean.TRUE);
                case 2 -> number.setIndex2(Boolean.TRUE);
            }
        });

        return Result.succ(number);
    }


    @GetMapping("/blogWSCooperate/{blogId}/{coNumber}")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    @ResponseBody
    public Result init(@PathVariable @CooperateBlogId Long blogId, @PathVariable Integer coNumber, HttpServletRequest request) {

        Long userId = MyUtils.reqToUserId(request);
        BlogEntity blog = blogService.getById(blogId);
        Assert.notNull(blog, "该博客不存在");

        UserEntity user = userService.getBaseMapper().selectOne(new QueryWrapper<UserEntity>().eq("id", userId).select("id", "username", "avatar", "role"));
        UserEntityVo userVo = new UserEntityVo();
        BeanUtils.copyProperties(user, userVo);

        userVo.setNumber(coNumber);
        userVo.setServerIpHost(RabbitConfig.serverMark);

        if (!redisTemplate.opsForHash().hasKey(Const.CO_PREFIX + blogId, userId.toString())) {
            redisTemplate.opsForHash().put(Const.CO_PREFIX + blogId, userId.toString(), userVo);
        }

        redisTemplate.expire(Const.CO_PREFIX + blogId, 6 * 60, TimeUnit.MINUTES);

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Const.CO_PREFIX + blogId);

        ArrayList<UserEntityVo> users = new ArrayList<>();

        entries.forEach((k, v) -> {
            UserEntityVo value = MyUtils.jsonToObj(v, UserEntityVo.class);
            value.setServerIpHost(null);
            users.add(value);
        });

        users.sort(Comparator.comparingInt(UserEntityVo::getNumber));

        String idStr = blogId.toString();
        InitDto dto = transferToDto(InitDto.Bind.class, InitDto.class,
                new Object[]{idStr, users}, new Class[]{idStr.getClass(), users.getClass()});
        rabbitTemplate.convertAndSend(
                RabbitConfig.WS_FANOUT_EXCHANGE,RabbitConfig.WS_BINDING_KEY  + RabbitConfig.serverMark,
                dto);

        log.info("{}号用户加入{}号编辑室", userId, blogId);

        HashMap<String, Object> map = new HashMap<>();
        map.put("blog", blog);
        map.put("users", users);
        return Result.succ(map);
    }


    @SneakyThrows
    private  <B, T> T transferToDto(Class<B> bClass, Class<T> tClass, Object[] args, Class<?>[] argsType) {
        B bInstance = bClass.getDeclaredConstructor(argsType).newInstance(args);
        Container<B> container = new Container<>();
        container.setData(bInstance);
        return tClass.getDeclaredConstructor(container.getClass()).newInstance(container);
    }


}
