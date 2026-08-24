package com.seven7m.kanban.common.security;

import java.util.UUID;

/**
 * 当前用户上下文（第一版为简单桩，后续接入 JWT / SaToken 后由拦截器填充）。
 * 创建/初始化数据时作为操作人使用。
 */
public class UserContext {

    /** 种子管理员固定 UUID，无鉴权令牌时的默认操作人回退（仅本地开发桩） */
    public static final UUID DEFAULT_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String DEFAULT_ADMIN_NAME = "admin";

    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_NAME = new ThreadLocal<>();

    public static void set(UUID userId, String username) {
        USER_ID.set(userId);
        USER_NAME.set(username);
    }

    public static void clear() {
        USER_ID.remove();
        USER_NAME.remove();
    }

    /**
     * 操作人 id，未设置时抛异常避免未登录用户被当作超级管理员。
     * 拦截器漏配或非请求上下文调用会立即暴露问题。
     */
    public static UUID getUserId() {
        UUID id = USER_ID.get();
        if (id == null) {
            throw new IllegalStateException("UserContext not set — interceptor missing or called outside request context");
        }
        return id;
    }

    /**
     * 操作人名称，未设置时抛异常。
     */
    public static String getUsername() {
        String name = USER_NAME.get();
        if (name == null) {
            throw new IllegalStateException("UserContext not set — interceptor missing or called outside request context");
        }
        return name;
    }
}
