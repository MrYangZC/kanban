package com.seven7m.kanban.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要系统管理员权限的接口。
 * 判定：当前用户在任一项目的 project_member 中拥有 OWNER / ADMIN 角色即视为管理员。
 * 由 {@link AdminAuthInterceptor} 统一校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
