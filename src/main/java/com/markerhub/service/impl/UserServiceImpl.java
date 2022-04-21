package com.markerhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.common.lang.Const;
import com.markerhub.entity.User;
import com.markerhub.mapper.UserMapper;
import com.markerhub.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.util.JwtUtils;
import com.markerhub.util.MyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    JwtUtils jwtUtils;

    @Autowired
    public void setJwtUtils(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void modifyUser(Integer id, Integer status) {
        boolean update = update(new UpdateWrapper<User>().eq("id", id).set("status", status));

        log.info("更新账户状态:{}", update);

        Assert.isTrue(update, "删除失败");

        MyUtil.deleteHot(Const.HOT_USERS_PREFIX, Const.HOT_BLOG_PREFIX);
    }

    @Override
    public Page<User> queryUsers(String role, Integer currentPage, Integer size) {
        Page<User> userPage = new Page<>(currentPage, size);
        Page<User> page;
        if (StringUtils.hasLength(role)) {//搜索
            page = page(userPage, new QueryWrapper<User>().eq("role", role));

        } else {//不是搜索
            page = page(userPage, new QueryWrapper<User>().select("id", "username", "avatar", "email", "status", "created", "last_login", "role").orderByAsc("created"));
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
        return page;
    }

    @Override
    public void addUser(User user) {
        User userExist = getBaseMapper().selectOne(new QueryWrapper<User>().eq("id", user.getId()));

        if (userExist == null) {//添加
            user.setPassword(SecureUtil.md5(user.getPassword()));
            user.setCreated(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());
            boolean update = saveOrUpdate(user);

            log.info("添加{}号账号结果:{}", user.getId(), update);

            Assert.isTrue(update, "添加失败");
        } else {//修改

            BeanUtil.copyProperties(user, userExist, "created", "password", "lastLogin", "username", "id");

            boolean update = saveOrUpdate(userExist);

            log.info("修改{}号账号结果:{}", userExist.getId(), update);

            Assert.isTrue(update, "修改失败");
            //删除缓存角色授权信息
            redisTemplate.delete(Const.ROLE_PREFIX + user.getId());
        }

        MyUtil.deleteHot(Const.HOT_USERS_PREFIX);
    }

    @Override
    public void deleteUsers(Long[] ids) {
        for (Long id : ids) {
            String role = getOne(new QueryWrapper<User>().eq("id", id)).getRole();
            if (Const.ADMIN.equals(role)) {
                throw new RuntimeException("不准删除管理员");
            }
        }

        ArrayList<Long> idList = new ArrayList<>(List.of(ids));

        boolean remove = removeByIds(idList);

        log.info("删除账号{}结果:{}", ids, remove);

        Assert.isTrue(remove, "删除失败");

        MyUtil.deleteHot(Const.HOT_USERS_PREFIX);
    }

    @Override
    public void roleKick(Long id) {
        //先进行锁定
        boolean update = update(new UpdateWrapper<User>().eq("id", id).set("status", 1));

        log.info("锁定账号{}结果:{}", id, update);

        Assert.isTrue(update, "锁定失败");
        //再对缓存进行更新赋值操作
        User user = getById(id);
        String jwt = jwtUtils.generateToken(id);

        //替换掉原来的user会话
        MyUtil.setUserToCache(jwt, user, (long) (6 * 10 * 60));
    }

    @Override
    public void getPassword(PasswordDto passwordDto) {
        boolean update = update(new UpdateWrapper<User>().eq("username", passwordDto.getUsername()).set("password", SecureUtil.md5(passwordDto.getPassword())));

        log.info("修改{}密码结果:{}", passwordDto.getUsername(), update);

        Assert.isTrue(update, "修改密码失败");
    }
}
