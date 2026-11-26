package com.seven7m.kanban.common.constant;

import java.util.Set;

/**
 * {@code UpdateIssueRequest.clearFields} 允许的取值（API 字段名，不是列名）。
 *
 * <p>这些字符串同时出现在三个地方：请求 DTO 的文档、服务端的清空分支、前端的"清空"按钮。
 * 此前三处各写一遍字面量，加一个字段要改三处且没有任何编译期保障；
 * 收敛成常量后，服务端的 switch 与校验共用同一份取值集合。
 */
public final class ClearFields {

    public static final String ASSIGNEE_ID = "assigneeId";
    public static final String VERIFIER_ID = "verifierId";
    public static final String PARENT_ID = "parentId";
    public static final String DONE_AT = "doneAt";
    public static final String SPRINT_ID = "sprintId";
    public static final String DUE_DATE = "dueDate";
    public static final String MODULE = "module";
    public static final String FIX_VERSION = "fixVersion";
    public static final String PLAN_START = "planStart";
    public static final String ITERATION = "iteration";
    public static final String NO_FIX_REASON = "noFixReason";
    /** 参与者/抄送不是标量列，清空由各自的全量替换分支处理 */
    public static final String PARTICIPANTS = "participants";
    public static final String CC_USERS = "ccUsers";

    /** 全部受支持的清空字段（用于校验与文档生成） */
    public static final Set<String> ALL = Set.of(
            ASSIGNEE_ID, VERIFIER_ID, PARENT_ID, DONE_AT, SPRINT_ID, DUE_DATE,
            MODULE, FIX_VERSION, PLAN_START, ITERATION, NO_FIX_REASON, PARTICIPANTS, CC_USERS);

    private ClearFields() {
    }
}
