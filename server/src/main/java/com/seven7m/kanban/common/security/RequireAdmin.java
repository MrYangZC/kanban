package com.seven7m.kanban.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要系统管理员权限的接口。
 * 判定：sys_user.is_admin = true（系统级管理员标识，与项目级角色无关），
 * 由 {@link AdminAuthInterceptor} 统一校验。
 *
 * 可标注在方法或 Controller 类上：标注在类上时对该类全部端点生效，
 * 推荐整个后台管理 Controller 用类级标注，避免新增方法忘记加注解导致接口裸奔。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
