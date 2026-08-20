package com.seven7m.kanban.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 项目级角色权限要求。置于 Controller 方法上，由 ProjectPermissionInterceptor 校验。
 * 默认要求至少是成员（OWNER / MEMBER），可写接口用；只读接口无需此注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectRole {

    /** 允许访问的角色，默认全部可写角色，与 ProjectRole.isMember 对齐。 */
    String[] required() default {
            com.seven7m.kanban.project.ProjectRole.OWNER,
            com.seven7m.kanban.project.ProjectRole.ADMIN,
            com.seven7m.kanban.project.ProjectRole.PM,
            com.seven7m.kanban.project.ProjectRole.DEV,
            com.seven7m.kanban.project.ProjectRole.QA,
            com.seven7m.kanban.project.ProjectRole.MEMBER
    };
}
