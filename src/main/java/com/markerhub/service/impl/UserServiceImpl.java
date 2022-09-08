package com.markerhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.common.lang.Const;
import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.entity.RoleEntity;
import com.markerhub.entity.UserEntity;
import com.markerhub.mapper.UserMapper;
import com.markerhub.service.RoleService;
import com.markerhub.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.utils.JwtUtils;
import com.markerhub.utils.MyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {


    BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    @Lazy
    public void setbCryptPasswordEncoder(BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    RoleService roleService;

    @Autowired
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }

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
        boolean update = update(new UpdateWrapper<UserEntity>().eq("id", id).set("status", status));

        log.info("更新账户状态:{}", update);

        Assert.isTrue(update, "删除失败");

    }

    @Override
    public Page<UserEntityVo> queryUsers(String role, Integer currentPage, Integer size) {
        Page<UserEntity> userPage = new Page<>(currentPage, size);
        Page<UserEntity> page;
        if (StringUtils.hasLength(role)) {//搜索
            page = page(userPage, new QueryWrapper<UserEntity>().eq("role", role));

        } else {//不是搜索
            page = page(userPage, new QueryWrapper<UserEntity>().select("id", "username", "avatar", "email", "status", "created", "last_login", "role").orderByAsc("created"));
        }
        List<UserEntity> records = page.getRecords();

        List<UserEntityVo> userVos = new ArrayList<>();

        records.forEach(user -> {
            UserEntityVo userVo = new UserEntityVo();
            BeanUtils.copyProperties(user, userVo);
            userVos.add(userVo);
        });

        userVos.forEach(record -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(Const.USER_PREFIX + record.getUsername())) && record.getStatus() == 0) {
                record.setMonitor(1);
            }
            String name = roleService.getOne(new QueryWrapper<RoleEntity>().select("name").eq("code", record.getRole())).getName();
            record.setRole(name);
        });

        Page<UserEntityVo> userVoPage = new Page<>();
        userVoPage.setRecords(userVos);
        userVoPage.setTotal(page.getTotal());
        userVoPage.setCurrent(page.getCurrent());
        userVoPage.setSize(page.getSize());
        return userVoPage;
    }

    @Override
    public void addUser(UserEntityVo user) {
        UserEntity userExist = getBaseMapper().selectOne(new QueryWrapper<UserEntity>().eq("id", user.getId()));

        if (userExist == null) {//添加
            user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
            user.setCreated(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());
            boolean update = saveOrUpdate(user);
            log.info("添加{}号账号结果:{}", user.getId(), update);

            Assert.isTrue(update, "添加失败");
        } else {//修改

            BeanUtils.copyProperties(user, userExist, "created", "password", "lastLogin", "username", "id");
            boolean update = saveOrUpdate(userExist);
            log.info("修改{}号账号结果:{}", userExist.getId(), update);
            Assert.isTrue(update, "修改失败");
        }

    }

    @Override
    @Transactional
    public void deleteUsers(Long[] ids) {
        for (Long id : ids) {
            String role = getOne(new QueryWrapper<UserEntity>().eq("id", id)).getRole();
            if (Const.ADMIN.equals(role)) {
                throw new RuntimeException("不准删除管理员");
            }
            UserEntity user = getById(id);
            boolean b = removeById(id);
            if (b) {
                MyUtils.setUserToCache(UUID.randomUUID().toString(), user, 604800L);
            }
            Assert.isTrue(b, "删除[" + id + "]失败");
        }

    }

    @Override
    public void roleKick(Long id) {
        //先进行锁定
        boolean update = update(new UpdateWrapper<UserEntity>().eq("id", id).set("status", 1));

        log.info("锁定账号{}结果:{}", id, update);

        Assert.isTrue(update, "锁定失败");
        //再对缓存进行更新赋值操作
        UserEntity user = getById(id);
        String jwt = jwtUtils.generateToken(user.getUsername());

        //替换掉原来的user会话
        MyUtils.setUserToCache(jwt, user, (long) (6 * 60 * 60));
    }

    @Override
    @Transactional
    public void getPassword(PasswordDto passwordDto) {
        boolean update = update(new UpdateWrapper<UserEntity>().eq("username", passwordDto.getUsername()).set("password", bCryptPasswordEncoder.encode(passwordDto.getPassword())));
        log.info("修改{}密码结果:{}", passwordDto.getUsername(), update);
        Assert.isTrue(update, "修改密码失败");
    }
}
