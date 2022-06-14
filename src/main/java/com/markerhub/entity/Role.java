package com.markerhub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 *
 * @TableName m_role
 */
@TableName(value ="m_role")
@EqualsAndHashCode(callSuper = false)
@Data
public class Role implements Serializable {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     *
     */
    @NotBlank(message = "角色名称不能为空")
    private String name;

    /**
     *
     */
    @NotBlank(message = "角色编码不能为空")
    private String code;

    /**
     * 备注
     */
    private String remark;

    /**
     *
     */
    private LocalDateTime created;
    private LocalDateTime updated;

    /**
     *
     */
    private Integer status;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableField(exist = false)
    private List<Long> menuIds = new ArrayList<>();
}
