package com.markerhub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 
 * @TableName m_role_menu
 */
@TableName(value ="m_role_menu")
@Data
public class RoleMenu implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long roleId;

    /**
     * 
     */
    private Long menuId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}