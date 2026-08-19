package com.seven7m.kanban.common.security;

import java.util.Set;

/**
 * 当前请求的项目角色上下文（RBAC：一个用户可拥有多个角色）。
 * 供业务层在需要处读取（如校验当前用户是否为只读）。
 */
public final class ProjectRoleContext {

    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();

    private ProjectRoleContext() {
    }

    public static void setRoles(Set<String> roles) {
        ROLES.set(roles);
    }

    public static Set<String> getRoles() {
        return ROLES.get();
    }

    public static void clear() {
        ROLES.remove();
    }
}
