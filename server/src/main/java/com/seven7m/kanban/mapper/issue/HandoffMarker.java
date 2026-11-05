package com.seven7m.kanban.mapper.issue;

import lombok.Data;

import java.util.UUID;

/**
 * 最近一条"交接 / 撤销交接"动态的投影，只用于判断撤销按钮是否可用。
 *
 * <p>与 {@link TimelineRow} 同属 {@code issue_activity} 表的读取模型；同样是 POJO 而不是 Map，
 * 列到属性的映射显式写在 {@code IssueTimelineMapper#latestHandoff} 的 {@code @Results} 上。
 */
@Data
public class HandoffMarker {

    /** 交接动态的动作码：与 {@code BaseWorkItemService} 写入值保持一致 */
    public static final String ACTION_ASSIGNEE_CHANGED = "ASSIGNEE_CHANGED";

    private UUID id;

    /** ASSIGNEE_CHANGED 或 HANDOFF_REVOKED */
    private String action;

    /** 交接后的负责人值，JSON 文本（含 userId） */
    private String newValue;

    /** 这条动态是否是"负责人变更"（撤销的前提） */
    public boolean isAssigneeChanged() {
        return ACTION_ASSIGNEE_CHANGED.equals(action);
    }
}
