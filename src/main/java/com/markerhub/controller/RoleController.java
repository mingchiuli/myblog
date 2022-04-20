package com.markerhub.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Role;
import com.markerhub.service.RoleMenuService;
import com.markerhub.service.RoleService;
import com.markerhub.service.UserService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * @author mingchiuli
 * @create 2022-02-25 6:26 PM
 */
@RestController
@RequestMapping("/sys/role")
public class RoleController {

    UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    RoleService roleService;

    @Autowired
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }

    RoleMenuService roleMenuService;

    @Autowired
    public void setRoleMenuService(RoleMenuService roleMenuService) {
        this.roleMenuService = roleMenuService;
    }

    @GetMapping("/info/{id}")
    @RequiresRoles(Const.ADMIN)
    public Result info(@PathVariable("id") Long id) {
        Role role = roleService.info(id);
        return Result.succ(role);
    }

    @GetMapping("/list")
    @RequiresRoles(value = {Const.ADMIN, Const.GIRL, Const.BOY}, logical = Logical.OR)
    public Result list(String name, Integer current, Integer size) {

        Page<Role> pageData = roleService.page(new Page<>(current, size),
                new QueryWrapper<Role>()
                        .like(StrUtil.isNotBlank(name), "name", name)
        );

        return Result.succ(pageData);
    }

    @PostMapping("/save")
    @RequiresRoles(Const.ADMIN)
    public Result save(@Validated @RequestBody Role role) {
        roleService.saveRole(role);
        return Result.succ(null);
    }

    @PostMapping("/update")
    @RequiresRoles(Const.ADMIN)
    public Result update(@Validated @RequestBody Role role) {
        roleService.updateRole(role);
        return Result.succ(null);
    }

    @PostMapping("/delete")
    @RequiresRoles(Const.ADMIN)
    public Result info(@RequestBody Long[] ids) {
        roleService.deleteRole(ids);
        return Result.succ(null);
    }

    @Transactional
    @PostMapping("/perm/{roleId}")
    @RequiresRoles(Const.ADMIN)
    public Result info(@PathVariable("roleId") Long roleId, @RequestBody Long[] menuIds) {
        menuIds = roleService.perm(roleId, menuIds);
        return Result.succ(menuIds);
    }


}
