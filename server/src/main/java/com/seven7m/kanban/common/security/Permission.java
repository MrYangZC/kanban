package com.seven7m.kanban.common.security;

/**
 * 项目内操作权限（接口声明的最小粒度）。
 * 角色 -> 操作权限的映射存数据库(role_permission 表)，运行时动态判定。
 */
public enum Permission {
    /** 查看项目内资源 */
    READ,
    /** 协作：创建/编辑/评论/附件等 */
    WRITE,
    /** 管理：成员、标签、状态等配置类操作 */
    MANAGE
}
