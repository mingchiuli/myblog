package com.markerhub.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.markerhub.common.vo.BlogPostDocumentVo;
import com.markerhub.common.vo.BlogEntityVo;
import com.markerhub.entity.BlogEntity;
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
public interface BlogService extends IService<BlogEntity> {

    Integer getYearCount(Integer year);

    List<BlogEntityVo> queryAllBlogs();

    boolean recover(BlogEntity blog);

    Page<BlogEntity> listByYear(Integer currentPage, Integer year);

    Page<BlogEntity> listBlogsByPage(Integer currentPage);

    BlogEntity getBlogDetail(Long id);

    BlogEntity getAuthorizedBlogDetail(Long id);

    BlogEntity getLockedBlog(Long blogId, String token);

    Page<BlogPostDocumentVo> selectBlogsByES(Integer currentPage, String keyword, Integer status);

    Page<BlogPostDocumentVo> selectYearBlogsByES(Integer currentPage, String keyword, Integer year, Integer status);

    void updateBlog(BlogEntityVo blog);

    Long initBlog();

    Page<BlogEntityVo> selectDeletedBlogs(String title, Integer currentPage, Integer size, Long userId);

    void recoverBlog(Long id, Long userId);

    void changeBlogStatus(Long id, Integer status);

    Page<BlogEntityVo> getAllBlogs(Integer currentPage, Integer size);

    Page<BlogEntityVo> queryBlogsAbstract(String keyword, Integer currentPage, Integer size);

    void deleteBlogs(Long[] ids);

    String getBlogToken();

    void setBlogToken();

    int[] searchYears();

    void setReadCount(Long id);
}
