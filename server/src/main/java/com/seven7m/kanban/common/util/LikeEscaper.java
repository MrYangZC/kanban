package com.seven7m.kanban.common.util;

/**
 * LIKE 模糊查询的通配符转义。
 *
 * <p>关键字是用户输入，其中的 {@code %} 与 {@code _} 在 LIKE 里是通配符：
 * 直接拼进 {@code "%" + kw + "%"} 会让「搜 _ 」变成「匹配全部」、「搜 100%」变成前缀匹配。
 * 三个 Service 曾经各写一份同样的 replace 链（其中一处还是内联的），这里统一。</p>
 *
 * <p>用法必须与 SQL 里的 {@code ESCAPE '\'} 配对出现，否则反斜杠会被当成普通字符：</p>
 * <pre>
 * String pattern = "%" + LikeEscaper.escape(keyword) + "%";
 * wrapper.apply("name LIKE {0} ESCAPE '\\'", pattern);
 * </pre>
 */
public final class LikeEscaper {

    private LikeEscaper() {
    }

    /**
     * 转义 LIKE 通配符。顺序不可调换：反斜杠必须先转义，
     * 否则后续为 {@code %} / {@code _} 添加的反斜杠会被二次转义。
     */
    public static String escape(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 便捷方法：生成两侧补 % 的模糊匹配串 */
    public static String contains(String raw) {
        return "%" + escape(raw == null ? "" : raw.trim()) + "%";
    }
}
