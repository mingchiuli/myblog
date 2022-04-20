package com.markerhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.entity.Role;
import com.markerhub.entity.RoleMenu;
import com.markerhub.service.RoleMenuService;
import com.markerhub.service.RoleService;
import com.markerhub.mapper.RoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author mingchiuli
* @description 针对表【m_role】的数据库操作Service实现
* @createDate 2022-02-25 10:48:04
*/
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role>
    implements RoleService{

    RoleMenuService roleMenuService;

    @Autowired
    public void setRoleMenuService(RoleMenuService roleMenuService) {
        this.roleMenuService = roleMenuService;
    }

    @Override
    @Transactional
    public Role info(Long id) {

        Role role = getById(id);

        // 获取角色相关联的菜单id
        List<RoleMenu> roleMenus = roleMenuService.list(new QueryWrapper<RoleMenu>().eq("role_id", id));
        List<Long> menuIds = roleMenus.stream().map(RoleMenu::getMenuId).collect(Collectors.toList());

        role.setMenuIds(menuIds);

        return role;
    }

    @Override
    public void saveRole(Role role) {

        role.setCreated(LocalDateTime.now());
        //0为开启，1为禁用
        role.setStatus(0);

        save(role);
    }

    @Override
    public void updateRole(Role role) {
        role.setUpdated(LocalDateTime.now());
        updateById(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long[] ids) {
        removeByIds(List.of(ids));
        // 删除中间表
        roleMenuService.remove(new QueryWrapper<RoleMenu>().in("role_id",  ids));
    }

    @Override
    @Transactional
    public Long[] perm(Long roleId, Long[] menuIds) {
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
        return menuIds;
    }
}




