package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.dto.PasswordDto;
import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.entity.UserEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
public interface UserService extends IService<UserEntity> {

    void modifyUser(Integer id, Integer status);

    Page<UserEntityVo> queryUsers(String role, Integer currentPage, Integer size);

    void addUser(UserEntityVo user);

    void deleteUsers(Long[] ids);

    void roleKick(Long id);

    void getPassword(PasswordDto passwordDto);

}
