package com.markerhub.mapper;

import com.markerhub.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
public interface BlogMapper extends BaseMapper<Blog> {

    Integer getYearCount(Integer year);

    List<Blog> queryAllBlogs();

    List<Blog> queryBlogs(String title);

    boolean recover(Blog blog);

}
