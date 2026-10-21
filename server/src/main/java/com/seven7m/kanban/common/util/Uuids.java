package com.seven7m.kanban.common.util;

import com.seven7m.kanban.common.exception.ServiceException;

import java.util.UUID;

/**
 * UUID 字符串解析工具。
 *
 * <p>全库曾有 12 处 `try { UUID.fromString(x) } catch (IllegalArgumentException ignored) {}`，
 * 每处语义还各不相同：有的解析失败返回 null、有的继续往下找别的路径变量、
 * 有的转成 400 业务异常。这里把三种意图显式命名，调用处只表达「我要哪种」。</p>
 */
public final class Uuids {

    private Uuids() {
    }

    /** 解析失败返回 null（用于「这个路径变量可能不是 UUID」的探测场景） */
    public static UUID parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 是否是合法 UUID，等价于 {@code parseOrNull(raw) != null} */
    public static boolean isUuid(String raw) {
        return parseOrNull(raw) != null;
    }

    /**
     * 解析失败抛 400 业务异常，文案与各 Service 原本手写的一致：
     * {@code "{fieldName} 格式非法: {raw}"}
     */
    public static UUID parseOrThrow(String raw, String fieldName) {
        UUID id = parseOrNull(raw);
        if (id == null) {
            throw new ServiceException(400, fieldName + " 格式非法: " + raw);
        }
        return id;
    }
}
