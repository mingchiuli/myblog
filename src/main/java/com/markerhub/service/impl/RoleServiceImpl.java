package com.markerhub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.markerhub.entity.Role;
import com.markerhub.service.RoleService;
import com.markerhub.mapper.RoleMapper;
import org.springframework.stereotype.Service;

/**
* @author mingchiuli
* @description 针对表【m_role】的数据库操作Service实现
* @createDate 2022-02-25 10:48:04
*/
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role>
    implements RoleService{

}




