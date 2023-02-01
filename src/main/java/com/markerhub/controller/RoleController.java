package com.markerhub.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.RoleEntity;
import com.markerhub.service.RoleMenuService;
import com.markerhub.service.RoleService;
import com.markerhub.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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

    RoleService roleService;

    RoleMenuService roleMenuService;

    public RoleController(UserService userService, RoleService roleService, RoleMenuService roleMenuService) {
        this.userService = userService;
        this.roleService = roleService;
        this.roleMenuService = roleMenuService;
    }

    @GetMapping("/info/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result info(@PathVariable("id") Long id) {
        RoleEntity role = roleService.info(id);
        return Result.succ(role);
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('admin', 'boy', 'girl')")
    public Result list(String name, Integer current, Integer size) {

        Page<RoleEntity> pageData = roleService.page(new Page<>(current, size),
                new QueryWrapper<RoleEntity>()
                        .like(StringUtils.hasLength(name), "name", name)
        );

        return Result.succ(pageData);
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('admin')")
    public Result save(@Validated @RequestBody RoleEntity role) {
        roleService.saveRole(role);
        return Result.succ(null);
    }

    @PostMapping("/update")
    @PreAuthorize("hasRole('admin')")
    public Result update(@Validated @RequestBody RoleEntity role) {
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
