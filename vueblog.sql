-- auto-generated definition
create table m_menu
(
    `menu_id`   bigint auto_increment
        primary key,
    `parent_id` bigint       null comment '父菜单ID，一级菜单为0',
    `title`     varchar(50)  null comment '菜单名称',
    `name`      varchar(50)  null,
    `url`       varchar(200) null comment '菜单URL',
    `component` varchar(200) null,
    `type`      int          null comment '类型   0：目录   1：菜单   2：按钮',
    `icon`      varchar(50)  null comment '菜单图标',
    `order_num` int          null comment '排序',
    `status` int not null comment '状态',
    key `UK_TITLE` (`title`) using btree
) ENGINE=InnoDB default charset utf8mb4;


INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (1, 0, '系统管理', '', null, null, 0, 'el-icon-setting', 1, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (2, 1, '访问统计', 'SystemIndex', '/sys/', 'sys/SystemIndex', 1, 'el-icon-location', 1, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (3, 1, '账号管理', 'SystemUsers', '/sys/users', 'sys/SystemUsers', 1, 'el-icon-user', 2, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (4, 1, '角色管理', 'SystemRoles', '/sys/roles', 'sys/SystemRoles', 1, 'el-icon-s-custom', 3, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (5, 1, '路由管理', 'SystemRoute', '/sys/menus', 'sys/SystemRoute', 1, 'el-icon-phone', 4, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (6, 1, '日志管理', 'SystemBlogs', '/sys/blogs', 'sys/SystemBlogs', 1, 'el-icon-edit-outline', 5, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (7, 1, '删除管理', 'SystemDeleted', '/sys/deleted', 'sys/SystemDeleted', 1, 'el-icon-delete', 6, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (8, 0, '工具管理', '', null, null, 0, 'el-icon-magic-stick', 2, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (9, 8, '系统日志', 'SystemLogs', '/sys/logs', 'sys/SystemLogs', 1, 'el-icon-s-management', 1, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (10, 8, '收藏检索', 'SystemWebs', '/sys/webs', 'sys/SystemWebs', 1, 'el-icon-link', 2, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (11, 8, '书籍下载', 'SystemBooks', '/sys/books', 'sys/SystemBooks', 1, 'el-icon-download', 3, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (12, 0, '编辑博客', 'BlogEdit', '/blog/:blogId/edit', 'BlogEdit', 1, '', 1, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (13, 0, '添加博客', 'BlogAdd', '/blogAdd', 'BlogEdit', 1, '', 1, 0);
INSERT INTO vueblog.m_menu (menu_id, parent_id, title, name, url, component, type, icon, order_num, status) VALUES (14, 0, '合作编辑', 'Cooperate', '/cooperate/:blogId/:coNumber', 'Cooperate', 1, '', 1, 0);

-- auto-generated definition
-- auto-generated definition
create table m_role
(
    id      bigint auto_increment
        primary key,
    name    varchar(64) unique not null,
    code    varchar(64) unique not null,
    remark  varchar(64) null comment '备注',
    created datetime    null,
    updated datetime    null,
    status  int         not null
) ENGINE=InnoDB default charset utf8mb4;


INSERT INTO vueblog.m_role (id, name, code, remark, created, updated, status) VALUES (1, '管理员', 'admin', '拥有所有权限', '2022-02-25 10:19:31', '2022-02-25 19:20:07', 0);
INSERT INTO vueblog.m_role (id, name, code, remark, created, updated, status) VALUES (2, '角色1', 'boy', '可以读写日志，不能删除', '2022-02-25 10:20:10', '2022-02-25 19:20:09', 0);
INSERT INTO vueblog.m_role (id, name, code, remark, created, updated, status) VALUES (3, '角色2', 'girl', '可以读写日志，不能删除', '2022-02-25 10:20:30', '2022-02-25 19:20:10', 0);
INSERT INTO vueblog.m_role (id, name, code, remark, created, updated, status) VALUES (4, '访客', 'guest', '只能登录后台查看基本功能', '2022-02-25 10:21:19', '2022-02-25 19:20:11', 0);
INSERT INTO vueblog.m_role (id, name, code, remark, created, updated, status) VALUES (5, '工具人', 'tooler', '提供增加书签权限的工具功能', '2022-02-25 10:21:56', '2022-02-25 19:20:13', 0);

-- auto-generated definition
create table m_role_menu
(
    id      bigint auto_increment
        primary key,
    role_id bigint not null,
    menu_id bigint not null
) ENGINE=InnoDB default charset utf8mb4;


INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (1, 1, 1);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (2, 1, 2);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (3, 1, 3);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (4, 1, 4);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (5, 1, 5);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (6, 1, 6);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (7, 1, 7);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (8, 1, 8);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (9, 1, 9);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (10, 1, 10);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (11, 1, 11);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (19, 3, 1);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (20, 3, 2);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (21, 3, 3);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (22, 3, 6);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (23, 3, 8);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (25, 3, 11);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (26, 4, 1);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (27, 4, 2);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (28, 4, 3);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (29, 4, 6);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (30, 4, 8);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (31, 4, 10);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (32, 4, 11);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (33, 5, 8);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (34, 5, 10);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (50, 2, 1);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (51, 2, 2);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (52, 2, 3);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (53, 2, 6);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (54, 2, 8);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (55, 2, 10);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (56, 2, 11);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (57, 1, 12);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (58, 2, 12);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (59, 3, 12);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (60, 1, 13);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (61, 2, 13);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (62, 3, 13);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (63, 1, 14);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (64, 2, 14);
INSERT INTO vueblog.m_role_menu (id, role_id, menu_id) VALUES (65, 3, 14);


