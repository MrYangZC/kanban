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
     * 操作人 id，未设置时回退默认管理员，避免审计字段为 null 或 ThreadLocal 串号。
     */
    public static UUID getUserId() {
        UUID id = USER_ID.get();
        return id != null ? id : DEFAULT_ADMIN_ID;
    }

    /**
     * 操作人名称，未设置时回退默认管理员名称。
     */
    public static String getUsername() {
        String name = USER_NAME.get();
        return name != null ? name : DEFAULT_ADMIN_NAME;
    }
}
