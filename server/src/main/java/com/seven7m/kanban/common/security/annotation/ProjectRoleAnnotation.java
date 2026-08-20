package com.seven7m.kanban.common.security.annotation;

import com.seven7m.kanban.common.security.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注接口需要的“项目操作权限”（而非具体角色）。
 * 角色 -> 操作权限的映射存于 role_permission 表，运行时动态判定，
 * 因此新增/调整角色无需改动代码。
 *
 * <p>权限语义：
 * <ul>
 *   <li>{@link Permission#READ}  查看项目内资源</li>
 *   <li>{@link Permission#WRITE} 协作（创建/编辑/评论/附件等）</li>
 *   <li>{@link Permission#MANAGE} 管理（成员/标签/状态等配置）</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectRoleAnnotation {
    Permission[] value() default {Permission.WRITE};
}
