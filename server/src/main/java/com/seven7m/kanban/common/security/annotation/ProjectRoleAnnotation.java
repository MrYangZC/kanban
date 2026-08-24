package com.seven7m.kanban.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注接口需要的细粒度权限码（如 "issue:create"、"sprint:manage"）。
 * 角色 -> 权限码的映射存于 role_permission 表，运行时动态判定，
 * 因此新增/调整角色或权限无需改动代码。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectRoleAnnotation {
    /** 权限码数组，默认查看权限 */
    String[] value() default {"issue:view"};
}
