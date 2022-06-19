package com.markerhub.mapper;

import com.markerhub.entity.RoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author mingchiuli
* @description 针对表【m_role】的数据库操作Mapper
* @createDate 2022-02-25 10:48:04
* @Entity com.markerhub.entity.Role
*/
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

}




