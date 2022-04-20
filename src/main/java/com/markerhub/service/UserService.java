package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
public interface UserService extends IService<User> {

    void modifyUser(Integer id, Integer status);

    Page<User> queryUsers(String role, Integer currentPage, Integer size);

    void addUser(User user);

    void deleteUsers(Long[] ids);

    void roleKick(Long id);

    void getPassword(PasswordDto passwordDto);

}
