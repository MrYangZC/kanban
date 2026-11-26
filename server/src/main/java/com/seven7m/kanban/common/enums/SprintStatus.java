package com.seven7m.kanban.common.enums;

/**
 * 迭代状态（对应 {@code sprint.status}）。
 *
 * <p>此前 {@code SprintService} 里直接写字面量 {@code "PLANNED"} / {@code "ACTIVE"}，
 * 与库里的取值靠人肉对齐；枚举化后状态机判断只剩一处事实源。
 */
public enum SprintStatus {

    /** 已规划，未开始 */
    PLANNED,

    /** 进行中 */
    ACTIVE,

    /** 已结束 */
    COMPLETED;

    /** 是否为该状态（null 安全） */
    public boolean matches(String value) {
        return name().equals(value);
    }
}
