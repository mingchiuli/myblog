package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.vo.BlogPostDocumentVo;
import com.markerhub.common.vo.BlogVo;
import com.markerhub.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
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

    List<BlogVo> queryAllBlogs();

    boolean recover(Blog blog);

    Page<Blog> listByYear(Integer currentPage, Integer year);

    Page<Blog> listBlogsByPage(Integer currentPage);

    Blog getBlogDetail(Long id);

    Blog getAuthorizedBlogDetail(Long id);

    Blog getLockedBlog(Long blogId, String token);

    Page<BlogPostDocumentVo> selectBlogsByES(Integer currentPage, String keyword);

    Page<BlogPostDocumentVo> selectYearBlogsByES(Integer currentPage, String keyword, Integer year);

    void updateBlog(BlogVo blog);

    Long initBlog();

    Page<BlogVo> selectDeletedBlogs(String title, Integer currentPage, Integer size, Long userId);

    void recoverBlog(Long id, Long userId);

    void changeBlogStatus(Long id, Integer status);

    Page<BlogVo> getAllBlogs(Integer currentPage, Integer size);

    Page<BlogVo> queryBlogsAbstract(String keyword, Integer currentPage, Integer size);

    void deleteBlogs(Long[] ids);

    String getBlogToken();

    void setBlogToken();

    int[] searchYears();


}
