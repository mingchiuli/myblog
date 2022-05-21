package com.markerhub.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.vo.BlogPostDocumentVo;
import com.markerhub.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.markerhub.search.model.BlogPostDocument;
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

    boolean recover(Blog blog);

    Page<Blog> listByYear(Integer currentPage, Integer year);

    Page<Blog> listBlogsByPage(Integer currentPage);

    Blog getBlogDetail(Long id);

    Blog getAuthorizedBlogDetail(Long id);

    Blog getLockedBlog(Long blogId, String token);

    Page<BlogPostDocumentVo> selectBlogsByES(Integer currentPage, String keyword);

    Page<BlogPostDocumentVo> selectYearBlogsByES(Integer currentPage, String keyword, Integer year);

    void updateBlog(Blog blog);

    Long initBlog();

    Page<Blog> selectDeletedBlogs(String title, Integer currentPage, Integer size, Long userId);

    void recoverBlog(Long id, Long userId);

    void changeBlogStatus(Long id, Integer status);

    Page<Blog> getAllBlogs(Integer currentPage, Integer size);

    Page<Blog> queryBlogsAbstract(String keyword, Integer currentPage, Integer size);

    void deleteBlogs(Long[] ids);

    String getBlogToken();

    void setBlogToken();

}
