package com.markerhub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.entity.RoleEntity;
import com.markerhub.entity.RoleMenuEntity;
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
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleEntity>
    implements RoleService{

    RoleMenuService roleMenuService;

    @Autowired
    public void setRoleMenuService(RoleMenuService roleMenuService) {
        this.roleMenuService = roleMenuService;
    }

    @Override
    @Transactional
    public RoleEntity info(Long id) {

        RoleEntity role = getById(id);

        // 获取角色相关联的菜单id
        List<RoleMenuEntity> roleMenus = roleMenuService.list(new QueryWrapper<RoleMenuEntity>().eq("role_id", id));
        List<Long> menuIds = roleMenus.stream().map(RoleMenuEntity::getMenuId).collect(Collectors.toList());

        role.setMenuIds(menuIds);

        return role;
    }

    @Override
    public void saveRole(RoleEntity role) {

        role.setCreated(LocalDateTime.now());
        //0为开启，1为禁用
        role.setStatus(0);

        save(role);
    }

    @Override
    public void updateRole(RoleEntity role) {
        role.setUpdated(LocalDateTime.now());
        updateById(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long[] ids) {
        removeByIds(List.of(ids));
        // 删除中间表
        roleMenuService.remove(new QueryWrapper<RoleMenuEntity>().in("role_id",  ids));
    }

    @Override
    @Transactional
    public Long[] perm(Long roleId, Long[] menuIds) {
        List<RoleMenuEntity> sysRoleMenus = new ArrayList<>();

        Arrays.stream(menuIds).forEach(menuId -> {
            RoleMenuEntity roleMenu = new RoleMenuEntity();
            roleMenu.setMenuId(menuId);
            roleMenu.setRoleId(roleId);

            sysRoleMenus.add(roleMenu);
        });

        // 先删除原来的记录，再保存新的
        roleMenuService.remove(new QueryWrapper<RoleMenuEntity>().eq("role_id", roleId));
        roleMenuService.saveBatch(sysRoleMenus);
        return menuIds;
    }
}




