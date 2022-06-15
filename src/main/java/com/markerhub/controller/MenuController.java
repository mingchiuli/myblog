package com.markerhub.controller;

import com.markerhub.common.lang.Result;
import com.markerhub.entity.MenuEntity;
import com.markerhub.service.MenuService;
import com.markerhub.service.RoleMenuService;
import com.markerhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author mingchiuli
 * @create 2022-02-25 11:18 AM
 */
@RestController
@RequestMapping("/sys/menu")
public class MenuController {

    RoleMenuService roleMenuService;

    @Autowired
    public void setRoleMenuService(RoleMenuService roleMenuService) {
        this.roleMenuService = roleMenuService;
    }

    UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    MenuService menuService;

    @Autowired
    public void setMenuService(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 用户当前用户的菜单和权限信息
     * @param
     * @return
     */
    @GetMapping("/nav")
    public Result nav(HttpServletRequest request) {
        List<MenuEntity> navs = menuService.nav(request);
        return Result.succ(navs);
    }


    @GetMapping("/info/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result info(@PathVariable(name = "id") Long id) {
        return Result.succ(menuService.getById(id));
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('admin')")
    public Result list() {

        List<MenuEntity> menus = menuService.tree();
        return Result.succ(menus);
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('admin')")
    public Result save(@Validated @RequestBody MenuEntity menu) {

        menuService.save(menu);

        return Result.succ(null);
    }

    @PostMapping("/update")
    @PreAuthorize("hasRole('admin')")
    public Result update(@Validated @RequestBody MenuEntity menu) {

        menuService.updateById(menu);

        return Result.succ(null);
    }

    @PostMapping("/delete/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result delete(@PathVariable("id") Long id) {
        menuService.delete(id);
        return Result.succ(null);
    }

}
