package com.seven7m.kanban.project;

import java.util.Set;

/**
 * 项目角色常量。角色字典存于 role 表（RBAC），此处仅提供静态常量与判断工具。
 */
public final class ProjectRole {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String PM = "PM";
    public static final String DEV = "DEV";
    public static final String QA = "QA";
    public static final String MEMBER = "MEMBER";
    public static final String VIEWER = "VIEWER";

    private ProjectRole() {
    }

    /** 已知角色集合（与 role 表种子数据一致） */
    public static final Set<String> ALL = Set.of(OWNER, ADMIN, PM, DEV, QA, MEMBER, VIEWER);

    /** 是否合法角色值 */
    public static boolean isValid(String role) {
        return role != null && ALL.contains(role);
    }

    /** 是否项目所有者（最高权限） */
    public static boolean isOwner(String role) {
        return OWNER.equals(role);
    }

    /** 是否管理员（可管理成员与项目设置） */
    public static boolean isAdmin(String role) {
        return OWNER.equals(role) || ADMIN.equals(role);
    }

    /** 是否至少是成员（可参与协作：创建/编辑/分配/拖拽） */
    public static boolean isMember(String role) {
        return role != null && (isAdmin(role) || PM.equals(role) || DEV.equals(role) || QA.equals(role) || MEMBER.equals(role));
    }

    /** 是否至少有只读权限 */
    public static boolean isViewer(String role) {
        return role != null && ALL.contains(role);
    }
}
