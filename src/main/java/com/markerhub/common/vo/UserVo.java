package com.markerhub.common.vo;

import com.markerhub.entity.User;
import lombok.Data;

import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2022-06-06 11:17 AM
 */
@Data
public class UserVo extends User implements Serializable {

    private Integer monitor;

    private Integer number;
}
