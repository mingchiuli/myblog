package com.markerhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.common.exception.InsertOrUpdateErrorException;
import com.markerhub.entity.MenuEntity;
import com.markerhub.entity.RoleMenuEntity;
import com.markerhub.entity.UserEntity;
import com.markerhub.mapper.UserMapper;
import com.markerhub.service.MenuService;
import com.markerhub.mapper.MenuMapper;
import com.markerhub.service.RoleMenuService;
import com.markerhub.service.UserService;
import com.markerhub.utils.MyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
* @author mingchiuli
* @description 针对表【m_menu(菜单管理)】的数据库操作Service实现
* @createDate 2022-02-25 10:52:53
*/
@Service
public class MenuServiceImpl extends ServiceImpl<MenuMapper, MenuEntity>
    implements MenuService{

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

    UserMapper userMapper;

    @Autowired
    public void setUserMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<MenuEntity> getCurrentUserNav(Long id) {

        String role = userService.getOne(new QueryWrapper<UserEntity>().eq("id", id).select("role")).getRole();
        List<Long> menuIds = userMapper.getNavMenuIds(role);
        List<MenuEntity> menus = this.listByIds(menuIds);
        // 转树状结构
        return buildTreeMenu(menus);

    }

    @Override
    public List<MenuEntity> tree() {
        // 获取所有菜单信息
        List<MenuEntity> menus = this.list(new QueryWrapper<MenuEntity>().orderByAsc("order_num"));

        // 转成树状结构
        return buildTreeMenu(menus);
    }

    @Override
    public List<MenuEntity> nav(HttpServletRequest request) {
        Long id = MyUtils.reqToUserId(request);
        return getCurrentUserNav(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        long count = count(new QueryWrapper<MenuEntity>().eq("parent_id", id));

        if (count > 0) {
            throw new InsertOrUpdateErrorException("请先删除子菜单");
        }

        removeById(id);

        // 同步删除中间关联表
        roleMenuService.remove(new QueryWrapper<RoleMenuEntity>().eq("menu_id", id));
    }


    public List<MenuEntity> buildTreeMenu(List<MenuEntity> menus) {
        //2.组装父子的树形结构
        //2.1 找到所有一级分类
        return menus.stream()
                .filter(menu -> menu.getParentId() == 0)
                .peek(menu-> menu.setChildren(getChildren(menu, menus)))
                .sorted(Comparator.comparingInt(menu -> (menu.getOrderNum() == null ? 0 : menu.getOrderNum())))
                .collect(Collectors.toList());
    }

    public List<MenuEntity> getChildren(MenuEntity root, List<MenuEntity> all) {
        return all.stream()
                .filter(menu -> Objects.equals(menu.getParentId(), root.getMenuId()))
                .peek(menu -> menu.setChildren(getChildren(menu, all)))
                .sorted(Comparator.comparingInt(menu -> (menu.getOrderNum() == null ? 0 : menu.getOrderNum())))
                .collect(Collectors.toList());
    }
}




