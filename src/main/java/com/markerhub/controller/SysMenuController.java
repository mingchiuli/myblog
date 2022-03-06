package com.markerhub.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.markerhub.common.lang.Const;
import com.markerhub.common.lang.Result;
import com.markerhub.entity.Menu;
import com.markerhub.entity.RoleMenu;
import com.markerhub.service.MenuService;
import com.markerhub.service.RoleMenuService;
import com.markerhub.service.UserService;
import com.markerhub.util.MyUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
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
public class SysMenuController {

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
    @RequiresAuthentication
    public Result nav(HttpServletRequest request) {

        Long id = MyUtils.reqToUserId(request);

        List<Menu> navs = menuService.getCurrentUserNav(id);

        return Result.succ(navs);
    }


    @GetMapping("/info/{id}")
    @RequiresRoles(Const.ADMIN)
    public Result info(@PathVariable(name = "id") Long id) {
        return Result.succ(menuService.getById(id));
    }

    @GetMapping("/list")
    @RequiresRoles(Const.ADMIN)
    public Result list() {

        List<Menu> menus = menuService.tree();
        return Result.succ(menus);
    }

    @PostMapping("/save")
    @RequiresRoles(Const.ADMIN)
    public Result save(@Validated @RequestBody Menu menu) {

        menuService.save(menu);

        return Result.succ(null);
    }

    @PostMapping("/update")
    @RequiresRoles(Const.ADMIN)
    public Result update(@Validated @RequestBody Menu menu) {

        menuService.updateById(menu);

        return Result.succ(null);
    }

    @PostMapping("/delete/{id}")
    @RequiresRoles(Const.ADMIN)
    @Transactional
    public Result delete(@PathVariable("id") Long id) {

        int count = menuService.count(new QueryWrapper<Menu>().eq("parent_id", id));

        if (count > 0) {
            return Result.fail("请先删除子菜单");
        }

        menuService.removeById(id);

        // 同步删除中间关联表
        roleMenuService.remove(new QueryWrapper<RoleMenu>().eq("menu_id", id));

        return Result.succ(null);
    }

}
