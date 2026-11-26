package com.seven7m.kanban.common.enums;

/**
 * 工作项状态所属阶段（对应 {@code issue_status.category}）。
 *
 * <p>库里存的是字符串，此前在 Service / SQL / Mapper 里各自写 {@code "DONE"}、{@code 'DONE'}，
 * 拼错一处就会出现"已完成不算完成"的静默错误。枚举化后编译器会挡住拼写问题。
 */
public enum IssueStatusCategory {

    /** 未开始（待办、待确认…） */
    TODO,

    /** 进行中（处理中、设计中、测试中…） */
    IN_PROGRESS,

    /** 已结束（已完成、已修复、已取消、暂不修复…） */
    DONE;

    /**
     * 宽松解析：null / 空串 / 未知取值返回 null（调用方按"没有该过滤条件"处理）。
     */
    public static IssueStatusCategory of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (IssueStatusCategory category : values()) {
            if (category.name().equalsIgnoreCase(trimmed)) {
                return category;
            }
        }
        return null;
    }

    /** 是否为该阶段（null 安全） */
    public static boolean is(String value, IssueStatusCategory expected) {
        return expected != null && expected.name().equals(value);
    }
}
