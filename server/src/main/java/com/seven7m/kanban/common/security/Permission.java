package com.seven7m.kanban.common.security;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

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
    public static final String ISSUE_ARCHIVE = "issue:archive";

    // ---- 迭代 ----
    public static final String SPRINT_VIEW   = "sprint:view";
    public static final String SPRINT_MANAGE = "sprint:manage";

    // ---- 里程碑 ----
    public static final String MILESTONE_VIEW   = "milestone:view";
    public static final String MILESTONE_MANAGE = "milestone:manage";

    // ---- 看板 ----
    // 说明：看板列的配置能力尚未落地，原来的 board:manage 从未被任何接口引用，
    // 已随数据库迁移删除；board:view 现在真正用于看板查询接口。
    public static final String BOARD_VIEW   = "board:view";

    // ---- 风险 ----
    // 说明：风险的增删改统一用 risk:manage（种子数据里 DEV/QA/MEMBER 只持有它），
    // 更细的 risk:create/edit/delete 从未被接口引用，已随迁移删除。
    public static final String RISK_VIEW   = "risk:view";
    public static final String RISK_MANAGE = "risk:manage";

    // ---- 项目设置 ----
    public static final String PROJECT_SETTING  = "project:setting";
    public static final String PROJECT_MEMBER   = "project:member";
    public static final String PROJECT_LABEL    = "project:label";
    public static final String PROJECT_WORKFLOW = "project:workflow";
    public static final String PROJECT_ARCHIVE  = "project:archive";
    public static final String PROJECT_DELETE   = "project:delete";

    // ---- 公告 ----
    public static final String ANNOUNCEMENT_MANAGE = "announcement:manage";

    /** 反射收集全部权限码常量，供超级管理员默认拥有所有权限使用 */
    public static List<String> allCodes() {
        List<String> codes = new ArrayList<>();
        for (Field f : Permission.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    Object v = f.get(null);
                    if (v != null) {
                        codes.add((String) v);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return codes;
    }
}
