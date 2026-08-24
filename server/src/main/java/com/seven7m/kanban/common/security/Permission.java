package com.seven7m.kanban.common.security;

/**
 * 细粒度权限码常量（按模块 × 操作）。
 * 角色 -> 权限码的映射存数据库(role_permission 表)，运行时动态判定。
 * 新增/调整权限只需在数据库加数据，无需改代码。
 */
public final class Permission {

    private Permission() {}

    // ---- 工作项 ----
    public static final String ISSUE_VIEW    = "issue:view";
    public static final String ISSUE_CREATE  = "issue:create";
    public static final String ISSUE_EDIT    = "issue:edit";
    public static final String ISSUE_DELETE  = "issue:delete";
    public static final String ISSUE_COMMENT = "issue:comment";
    public static final String ISSUE_STATUS  = "issue:status";
    public static final String ISSUE_ASSIGN  = "issue:assign";
    public static final String ISSUE_EXPORT  = "issue:export";

    // ---- 迭代 ----
    public static final String SPRINT_VIEW   = "sprint:view";
    public static final String SPRINT_MANAGE = "sprint:manage";

    // ---- 里程碑 ----
    public static final String MILESTONE_VIEW   = "milestone:view";
    public static final String MILESTONE_MANAGE = "milestone:manage";

    // ---- 看板 ----
    public static final String BOARD_VIEW   = "board:view";
    public static final String BOARD_MANAGE = "board:manage";

    // ---- 风险 ----
    public static final String RISK_VIEW   = "risk:view";
    public static final String RISK_MANAGE = "risk:manage";

    // ---- 项目设置 ----
    public static final String PROJECT_SETTING  = "project:setting";
    public static final String PROJECT_MEMBER   = "project:member";
    public static final String PROJECT_ROLE     = "project:role";
    public static final String PROJECT_LABEL    = "project:label";
    public static final String PROJECT_WORKFLOW = "project:workflow";

    // ---- 公告 ----
    public static final String ANNOUNCEMENT_MANAGE = "announcement:manage";
}
