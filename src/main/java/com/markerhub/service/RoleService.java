package com.markerhub.service;

import com.markerhub.entity.RoleEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author mingchiuli
* @description 针对表【m_role】的数据库操作Service
* @createDate 2022-02-25 10:48:04
*/
public interface RoleService extends IService<RoleEntity> {

    RoleEntity info(Long id);

    void saveRole(RoleEntity role);

    void updateRole(RoleEntity role);

    void deleteRole(Long[] ids);

    Long[] perm(Long roleId, Long[] menuIds);
}
