package com.markerhub.common.vo;

import com.markerhub.entity.BlogEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2022-06-06 11:17 AM
 */
@Data
@EqualsAndHashCode(callSuper=true)
public class BlogEntityVo extends BlogEntity implements Serializable {

    private Integer readSum;

    private Integer readRecent;

    //文章对应的用户名字
    private String username;
}
