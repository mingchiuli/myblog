package com.markerhub.mapper;

import com.markerhub.entity.MenuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author mingchiuli
* @description 针对表【m_menu(菜单管理)】的数据库操作Mapper
* @createDate 2022-02-25 10:52:53
* @Entity com.markerhub.entity.Menu
*/
@Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {

}




