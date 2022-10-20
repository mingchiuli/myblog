package com.markerhub.mapper;

import com.markerhub.common.vo.BlogEntityVo;
import com.markerhub.entity.BlogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
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
@Mapper
public interface BlogMapper extends BaseMapper<BlogEntity> {

    Integer getYearCount(Integer year);

    List<BlogEntityVo> queryAllBlogs();

    List<BlogEntity> queryBlogs(String title);

    boolean recover(BlogEntity blog);

    int getPageCount(@Param("ldt") String ldt);

    int getPageYearCount(@Param("ldt") String ldt, @Param("year") int year);

    List<Integer> searchYears();

    boolean setReadSum(Long id);

}
