package com.markerhub.common.vo;

import com.markerhub.entity.UserEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2022-06-06 11:17 AM
 */
@Data
public class UserEntityVo extends UserEntity implements Serializable {

    private Integer monitor;

    private Integer number;
}
