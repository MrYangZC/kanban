package com.seven7m.kanban.mapper.issue;

import com.seven7m.kanban.common.util.Uuids;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 时间线（评论 + 动态 UNION ALL）的一行结果。
 *
 * <p>此前 `IssueTimelineMapper` 直接返回 `Map<String, Object>`，取值靠字符串键：
 * 列别名改一个字、或者写错 `newvalue`/`new_value`，编译期毫无提示，线上表现是
 * "动态里的旧值/新值/负责人莫名其妙是空的"。这里把列名收成常量、把类型转换收进工厂，
 * 键写错至少能在同一个文件里被发现，转换逻辑也有单测覆盖。
 *
 * <p>注意 `old_value`/`new_value` 是 JSON 文本（见 {@code ActivityValue}），这里只负责原样取出，
 * 翻译成中文由 {@code ActivityTextResolver} 负责。
 */
public record TimelineRow(
        UUID id,
        String type,
        UUID actorId,
        String content,
        UUID parentId,
        String oldValue,
        String newValue,
        LocalDateTime createdAt) {

    /** 与 {@code IssueTimelineMapper} 里 SELECT 的列别名一一对应 */
    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_ACTOR_ID = "actor_id";
    public static final String COL_CONTENT = "content";
    public static final String COL_PARENT_ID = "parent_id";
    public static final String COL_OLD_VALUE = "old_value";
    public static final String COL_NEW_VALUE = "new_value";
    public static final String COL_CREATED_AT = "created_at";

    /** 把 JDBC 行转成类型化对象；任何列缺失都退化为 null，不抛异常（历史数据/驱动差异） */
    public static TimelineRow from(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new TimelineRow(
                Uuids.parseOrNull(text(row, COL_ID)),
                text(row, COL_TYPE),
                Uuids.parseOrNull(text(row, COL_ACTOR_ID)),
                text(row, COL_CONTENT),
                Uuids.parseOrNull(text(row, COL_PARENT_ID)),
                text(row, COL_OLD_VALUE),
                text(row, COL_NEW_VALUE),
                dateTime(row, COL_CREATED_AT));
    }

    private static String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : value.toString();
    }

    private static LocalDateTime dateTime(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
