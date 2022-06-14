package com.markerhub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单管理
 * @TableName m_menu
 */
@TableName(value ="m_menu")
@EqualsAndHashCode(callSuper = false)
@Data
public class Menu implements Serializable {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Long menuId;

    /**
     * 父菜单ID，一级菜单为0
     */
    private Long parentId;

    /**
     * 菜单名称
     */
    private String title;

    private String name;

    /**
     * 菜单URL
     */
    private String url;

    private String component;

    /**
     * 类型   0：目录   1：菜单   2：按钮
     */
    private Integer type;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 排序
     */
    private Integer orderNum;

    private Integer status;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableField(exist = false)
    private List<Menu> children = new ArrayList<>();

}
