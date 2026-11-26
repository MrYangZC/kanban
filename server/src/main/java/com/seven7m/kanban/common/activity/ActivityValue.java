package com.seven7m.kanban.common.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * `issue_activity.old_value / new_value` 里机器值的编解码**唯一实现**。
 *
 * <p>这套结构此前是"隐式协议"：写入方（{@code AbstractWorkItemService}）手拼 JSON 字符串，
 * 读取方（{@code ActivityTextResolver}）手写解析。两边各写一遍意味着改结构要改两处，
 * 且拼串没有类型安全（转义写错、字段名拼错都只能等线上看到乱码才发现）。
 *
 * <p>保持与历史数据完全兼容，因此仍然按"小写字段名 + 扁平对象"序列化：
 * <ul>
 *   <li>状态流转：{@code {"status": "REQ_TODO"}}（存 code，展示时翻译）</li>
 *   <li>负责人变更：{@code {"user": "张三", "userId": "..."}}</li>
 *   <li>交接前负责人：{@code {"user": "张三", "userId": "...", "sprintId": "..."}}，
 *       sprintId 即使为空也写空串，撤销时需要区分"本来没有迭代"与"历史记录没记这个字段"</li>
 *   <li>交接落点：{@code {"sprint": "S1"}} / {@code {"label": "bug"}}</li>
 * </ul>
 */
public final class ActivityValue {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ActivityValue() {
    }

    // ---------------- 编码 ----------------

    /** {@code {"status": "CODE"}}；code 为空返回 null（动态表设计允许空值） */
    public static String status(String code) {
        return code == null ? null : field("status", code);
    }

    /** {@code {"user": "张三", "userId": "..."}}；两者都为空返回 null */
    public static String user(String name, UUID userId) {
        if (name == null && userId == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{\"user\": \"").append(escape(name == null ? "" : name)).append("\"");
        if (userId != null) {
            sb.append(", \"userId\": \"").append(userId).append("\"");
        }
        return sb.append('}').toString();
    }

    /**
     * 交接前的负责人值，额外带交接前的迭代 id。
     *
     * <p>即使交接前无人负责（{@code user} 为 null）也必须返回对象：
     * 撤销要靠 {@code sprintId} 判断"交接前没有迭代"，整条值丢了就退不回去。
     */
    public static String userWithSprint(String name, UUID userId, UUID sprintId) {
        String base = user(name, userId);
        if (base == null) {
            base = "{\"user\": \"\"}";
        }
        String sprint = sprintId == null ? "" : sprintId.toString();
        return base.substring(0, base.length() - 1) + ", \"sprintId\": \"" + sprint + "\"}";
    }

    /** 通用单字段值：{@code {"sprint":"S1"}} / {@code {"label":"bug"}} */
    public static String field(String field, String value) {
        return value == null ? null : "{\"" + field + "\": \"" + escape(value) + "\"}";
    }

    // ---------------- 解码 ----------------

    /**
     * 取某个字符串字段；不存在、非 JSON、解析失败都返回 null。
     *
     * <p>注意 null 与空串语义不同：空串表示"写入了但值为空"（如交接前没有迭代），
     * 调用方靠它区分"没有这个字段"。
     */
    public static String text(String raw, String field) {
        JsonNode node = parse(raw);
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText("");
    }

    /** 取交接动态里的 {@code userId}；历史记录没有该字段时返回 null（按"不可撤销"处理，不按姓名反查） */
    public static UUID userId(String raw) {
        String text = text(raw, "userId");
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 取交接动态里的 {@code sprintId}；空串返回 null（表示交接前没有迭代） */
    public static UUID sprintId(String raw) {
        String text = text(raw, "sprintId");
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 只解析 `{` 开头的对象；其余（含历史纯文本值）返回 null，由调用方决定如何兜底 */
    public static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
