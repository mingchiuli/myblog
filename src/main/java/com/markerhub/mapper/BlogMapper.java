package com.markerhub.mapper;

import com.markerhub.common.vo.BlogVo;
import com.markerhub.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
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

    List<BlogVo> queryAllBlogs();

    List<Blog> queryBlogs(String title);

    boolean recover(Blog blog);

    int getPageCount(@Param("ldt") String ldt);

    int getPageYearCount(@Param("ldt") String ldt, @Param("year") int year);

    List<Integer> searchYears();

}
