package com.markerhub.service;

import com.markerhub.entity.Menu;
import com.baomidou.mybatisplus.extension.service.IService;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author mingchiuli
* @description 针对表【m_menu(菜单管理)】的数据库操作Service
* @createDate 2022-02-25 10:52:53
*/
public interface MenuService extends IService<Menu> {

    List<Menu> getCurrentUserNav(Long id);

    List<Menu> tree();

    List<Menu> nav(HttpServletRequest request);

    void delete(Long id);
}
