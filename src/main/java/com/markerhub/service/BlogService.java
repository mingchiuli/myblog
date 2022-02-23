package com.markerhub.service;

import com.markerhub.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
public interface BlogService extends IService<Blog> {

    Integer getYearCount(Integer year);

    List<Blog> queryAllBlogs();

    List<Blog> queryBlogs(String title);

    boolean recover(Blog blog);

}
