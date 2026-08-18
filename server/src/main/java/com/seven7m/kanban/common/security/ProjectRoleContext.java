package com.seven7m.kanban.common.security;

/**
 * 当前请求的项目角色上下文。供业务层在需要处读取（如校验当前用户是否为只读）。
 */
public final class ProjectRoleContext {

    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private ProjectRoleContext() {
    }

    public static void set(String role) {
        ROLE.set(role);
    }

    public static String get() {
        return ROLE.get();
    }

    public static void clear() {
        ROLE.remove();
    }
}
