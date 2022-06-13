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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('admin')")
    public Result info(@PathVariable("id") Long id) {
        Role role = roleService.info(id);
        return Result.succ(role);
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    public Result list(String name, Integer current, Integer size) {

        Page<Role> pageData = roleService.page(new Page<>(current, size),
                new QueryWrapper<Role>()
                        .like(StrUtil.isNotBlank(name), "name", name)
        );

        return Result.succ(pageData);
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('admin')")
    public Result save(@Validated @RequestBody Role role) {
        roleService.saveRole(role);
        return Result.succ(null);
    }

    @PostMapping("/update")
    @PreAuthorize("hasRole('admin')")
    public Result update(@Validated @RequestBody Role role) {
        roleService.updateRole(role);
        return Result.succ(null);
    }

    @PostMapping("/delete")
    @PreAuthorize("hasRole('admin')")
    public Result info(@RequestBody Long[] ids) {
        roleService.deleteRole(ids);
        return Result.succ(null);
    }

    @Transactional
    @PostMapping("/perm/{roleId}")
    @PreAuthorize("hasRole('admin')")
    public Result info(@PathVariable("roleId") Long roleId, @RequestBody Long[] menuIds) {
        menuIds = roleService.perm(roleId, menuIds);
        return Result.succ(menuIds);
    }


}
