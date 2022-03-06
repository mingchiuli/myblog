package com.markerhub.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.cache.Cache;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.User;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtils;
import com.markerhub.util.MyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mingchiuli
 * @create 2022-03-06 6:55 PM
 */
@RestController
@Slf4j
public class UserController {

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    JwtUtils jwtUtils;

    @Autowired
    private void setJwtUtils(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    /**
     * 更改账户状态，0可以正常使用，1禁用
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/modifyUser/{id}/{status}")
    public Result modifyUser(@PathVariable Integer id, @PathVariable Integer status) {

        boolean update = userService.update(new UpdateWrapper<User>().eq("id", id).set("status", status));

        log.info("更新账户状态:{}", update);

        Assert.isTrue(update, "删除失败");

        MyUtils.deleteHot(Const.HOT_USERS_PREFIX, Const.HOT_BLOG_PREFIX);

        return Result.succ(null);
    }

    /**
     * 查询账号
     */
    @RequiresRoles(value = {Const.ADMIN, Const.BOY, Const.GIRL, Const.GUEST}, logical = Logical.OR)
    @Cache(name = Const.HOT_USERS)
    @GetMapping("/queryUsers")
    public Result queryUsers(@RequestParam String role, @RequestParam Integer currentPage, @RequestParam Integer size) {

        Page<User> userPage = new Page<>(currentPage, size);
        Page<User> page;
        if (StringUtils.hasLength(role)) {//搜索
            page = userService.page(userPage, new QueryWrapper<User>().eq("role", role));

        } else {//不是搜索
            page = userService.page(userPage, new QueryWrapper<User>().select("id", "username", "avatar", "email", "status", "created", "last_login", "role").orderByAsc("created"));
        }
        List<User> records = page.getRecords();
        for (User record : records) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.USER_PREFIX + record.getId())) && record.getStatus() == 0) {
                record.setMonitor(1);
            } else {
                record.setMonitor(0);
            }
        }
        page.setRecords(records);
        return Result.succ(page);
    }


    /**
     * 新增账号，修改信息
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/addUser")
    public Result addUser(@Validated @RequestBody User user) {

        User userExist = userService.getBaseMapper().selectOne(new QueryWrapper<User>().eq("id", user.getId()));

        if (userExist == null) {//添加
            user.setPassword(SecureUtil.md5(user.getPassword()));
            user.setCreated(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());
            boolean update = userService.saveOrUpdate(user);

            log.info("添加{}号账号结果:{}", user.getId(), update);

            Assert.isTrue(update, "添加失败");
        } else {//修改

            BeanUtil.copyProperties(user, userExist, "created", "password", "lastLogin", "username", "id");

            boolean update = userService.saveOrUpdate(userExist);

            log.info("修改{}号账号结果:{}", userExist.getId(), update);

            Assert.isTrue(update, "修改失败");
            //删除缓存角色授权信息
            redisTemplate.delete(Const.ROLE_PREFIX + user.getId());
        }

        MyUtils.deleteHot(Const.HOT_USERS_PREFIX);

        return Result.succ(null);
    }

    /**
     * 删除账号，批量
     * @param ids
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/deleteUsers")
    public Result deleteUsers(@RequestBody Long[] ids) {

        for (Long id : ids) {
            String role = userService.getOne(new QueryWrapper<User>().eq("id", id)).getRole();
            if (Const.ADMIN.equals(role)) {
                throw new RuntimeException("不准删除管理员");
            }
        }

        ArrayList<Long> idList = new ArrayList<>(List.of(ids));

        boolean remove = userService.removeByIds(idList);

        log.info("删除账号{}结果:{}", ids, remove);

        Assert.isTrue(remove, "删除失败");

        MyUtils.deleteHot(Const.HOT_USERS_PREFIX);

        return Result.succ(null);
    }

    /**
     * 回显信息
     * @param id
     * @return
     */
    @RequiresAuthentication
    @GetMapping("/getInfoById/{id}")
    public Result getRoleId(@PathVariable Long id) {
        User user = userService.getBaseMapper().selectOne(new QueryWrapper<User>().eq("id", id).select("id", "username", "email", "role", "avatar", "status"));
        return Result.succ(user);
    }

    /**
     * 踢人下线
     * @param id
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @GetMapping("/roleKick/{id}")
    public Result roleKick(@PathVariable Long id) {

        //先进行锁定
        boolean update = userService.update(new UpdateWrapper<User>().eq("id", id).set("status", 1));

        log.info("锁定账号{}结果:{}", id, update);

        Assert.isTrue(update, "锁定失败");
        //再对缓存进行更新赋值操作
        User user = userService.getById(id);
        String jwt = jwtUtils.generateToken(id);

        //替换掉原来的user会话
        MyUtils.setUserToCache(jwt, user, (long) (6 * 10 * 60));

        return Result.succ(null);
    }

    /**
     * 密码修改
     * @param passwordDto
     * @return
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/modifyPassword")
    public Result getPassword(@Validated @RequestBody PasswordDto passwordDto) {

        boolean update = userService.update(new UpdateWrapper<User>().eq("username", passwordDto.getUsername()).set("password", SecureUtil.md5(passwordDto.getPassword())));

        log.info("修改{}密码结果:{}", passwordDto.getUsername(), update);

        Assert.isTrue(update, "修改密码失败");

        return Result.succ(null);
    }
}
