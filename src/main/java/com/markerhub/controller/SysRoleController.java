package com.markerhub.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Role;
import com.markerhub.entity.RoleMenu;
import com.markerhub.service.RoleMenuService;
import com.markerhub.service.RoleService;
import com.markerhub.service.UserService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mingchiuli
 * @create 2022-02-25 6:26 PM
 */
@RestController
@RequestMapping("/sys/role")
public class SysRoleController {

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

        Role role = roleService.getById(id);

        // 获取角色相关联的菜单id
        List<RoleMenu> roleMenus = roleMenuService.list(new QueryWrapper<RoleMenu>().eq("role_id", id));
        List<Long> menuIds = roleMenus.stream().map(RoleMenu::getMenuId).collect(Collectors.toList());

        role.setMenuIds(menuIds);
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

        role.setCreated(LocalDateTime.now());
        //0为开启，1为禁用
        role.setStatus(0);

        roleService.save(role);
        return Result.succ(null);
    }

    @PostMapping("/update")
    @RequiresRoles(Const.ADMIN)
    public Result update(@Validated @RequestBody Role role) {

        role.setUpdated(LocalDateTime.now());

        roleService.updateById(role);

        return Result.succ(null);
    }

    @PostMapping("/delete")
    @RequiresRoles(Const.ADMIN)
    @Transactional
    public Result info(@RequestBody Long[] ids) {

        roleService.removeByIds(List.of(ids));
        // 删除中间表
        roleMenuService.remove(new QueryWrapper<RoleMenu>().in("role_id",  ids));

        return Result.succ(null);
    }

    @Transactional
    @PostMapping("/perm/{roleId}")
    @RequiresRoles(Const.ADMIN)
    public Result info(@PathVariable("roleId") Long roleId, @RequestBody Long[] menuIds) {

        List<RoleMenu> sysRoleMenus = new ArrayList<>();

        Arrays.stream(menuIds).forEach(menuId -> {
            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setMenuId(menuId);
            roleMenu.setRoleId(roleId);

            sysRoleMenus.add(roleMenu);
        });

        // 先删除原来的记录，再保存新的
        roleMenuService.remove(new QueryWrapper<RoleMenu>().eq("role_id", roleId));
        roleMenuService.saveBatch(sysRoleMenus);

        return Result.succ(menuIds);
    }


}
