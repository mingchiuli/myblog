package com.markerhub.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.cache.Cache;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.User;
import com.markerhub.service.UserService;
import com.markerhub.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * @author mingchiuli
 * @create 2022-03-06 6:55 PM
 */
@RestController
@Slf4j
public class UserController {

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
        userService.modifyUser(id, status);
        return Result.succ(null);
    }

    /**
     * 查询账号
     */
    @RequiresRoles(value = {Const.ADMIN, Const.BOY, Const.GIRL, Const.GUEST}, logical = Logical.OR)
    @Cache(name = Const.HOT_USERS)
    @GetMapping("/queryUsers")
    public Result queryUsers(@RequestParam String role, @RequestParam Integer currentPage, @RequestParam Integer size) {

        Page<User> page = userService.queryUsers(role, currentPage, size);
        return Result.succ(page);
    }


    /**
     * 新增账号，修改信息
     */
    @RequiresRoles(Const.ADMIN)
    @PostMapping("/addUser")
    public Result addUser(@Validated @RequestBody User user) {
        userService.addUser(user);
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
        userService.deleteUsers(ids);
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
        userService.roleKick(id);
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
        userService.getPassword(passwordDto);
        return Result.succ(null);
    }
}
