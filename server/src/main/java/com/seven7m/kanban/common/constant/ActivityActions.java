package com.seven7m.kanban.common.constant;

/**
 * {@code issue_activity.action} 的取值。
 *
 * <p>动态是审计数据：前端按 action 渲染图标与文案，后端按 action 反查"最后一次交接"。
 * 字面量分散在服务端多处时，改一个值就会让历史动态从时间线上"消失"（前端不认识新值）。
 */
public final class ActivityActions {

    /** 新建工作项 */
    public static final String CREATED = "CREATED";
    /** 状态流转（old/new 为 {"status": CODE}） */
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    /** 交接：负责人由 A 变为 B */
    public static final String ASSIGNEE_CHANGED = "ASSIGNEE_CHANGED";
    /** 撤销交接：负责人由 B 退回 A（与交接区分，避免撤销本身又成为可撤销项） */
    public static final String HANDOFF_REVOKED = "HANDOFF_REVOKED";
    /** 交接落点：工作项被放进某个迭代 */
    public static final String SPRINT_CHANGED = "SPRINT_CHANGED";
    /** 交接落点：工作项被补上某个标签 */
    public static final String LABEL_ADDED = "LABEL_ADDED";

    private ActivityActions() {
    }
}
