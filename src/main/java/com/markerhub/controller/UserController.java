package com.markerhub.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.common.lang.Result;
import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.entity.UserEntity;
import com.markerhub.service.UserService;
import com.markerhub.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/modifyUser/{id}/{status}")
    public Result modifyUser(@PathVariable Integer id, @PathVariable Integer status) {
        userService.modifyUser(id, status);
        return Result.succ(null);
    }

    /**
     * 查询账号
     */
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl', 'guest')")
    @GetMapping("/queryUsers")
    public Result queryUsers(@RequestParam String role, @RequestParam Integer currentPage, @RequestParam Integer size) {

        Page<UserEntityVo> page = userService.queryUsers(role, currentPage, size);
        return Result.succ(page);
    }


    /**
     * 新增账号，修改信息
     */
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/addUser")
    public Result addUser(@Validated @RequestBody UserEntityVo user) {
        userService.addUser(user);
        return Result.succ(null);
    }

    /**
     * 删除账号，批量
     * @param ids
     * @return
     */
    @PreAuthorize("hasRole('admin')")
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
    @GetMapping("/getInfoById/{id}")
    public Result getRoleId(@PathVariable Long id) {
        UserEntity user = userService.getBaseMapper().selectOne(new QueryWrapper<UserEntity>().eq("id", id).select("id", "username", "email", "role", "avatar", "status"));
        return Result.succ(user);
    }

    /**
     * 踢人下线
     * @param id
     * @return
     */
    @PreAuthorize("hasRole('admin')")
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
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/modifyPassword")
    public Result getPassword(@Validated @RequestBody PasswordDto passwordDto) {
        userService.getPassword(passwordDto);
        return Result.succ(null);
    }
}
