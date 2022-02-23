package com.markerhub.service.impl;

import com.markerhub.entity.Blog;
import com.markerhub.mapper.BlogMapper;
import com.markerhub.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Li MingChiu
 * @since 2021-10-27
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    BlogMapper blogMapper;

    @Autowired
    private void setBlogMapper(BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
    }


    @Override
    @Transactional
    public Integer getYearCount(Integer year) {
        return blogMapper.getYearCount(year);
    }

    @Override
    @Transactional
    public List<Blog> queryAllBlogs() {
        return blogMapper.queryAllBlogs();
    }

    @Override
    @Transactional
    public List<Blog> queryBlogs(String title) {
        return blogMapper.queryBlogs(title);
    }

    @Override
    @Transactional
    public boolean recover(Blog blog) {
        return blogMapper.recover(blog);
    }
}
