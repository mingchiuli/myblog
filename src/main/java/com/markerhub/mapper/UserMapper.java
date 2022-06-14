package com.markerhub.mapper;

import com.markerhub.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
public interface UserMapper extends BaseMapper<User> {

    List<Long> getNavMenuIds(String role);
}
