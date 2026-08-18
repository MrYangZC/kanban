package com.seven7m.kanban.project;

/**
 * 项目成员角色常量。project_member.role 以 VARCHAR 存储，避免再建枚举表。
 */
public final class ProjectRole {

    public static final String OWNER = "OWNER";
    public static final String MEMBER = "MEMBER";
    public static final String VIEWER = "VIEWER";

    private ProjectRole() {
    }

    /** 是否合法角色值 */
    public static boolean isValid(String role) {
        return OWNER.equals(role) || MEMBER.equals(role) || VIEWER.equals(role);
    }

    /** 是否项目所有者（最高权限） */
    public static boolean isOwner(String role) {
        return OWNER.equals(role);
    }

    /** 是否至少是成员（可参与协作：创建/编辑/分配/拖拽） */
    public static boolean isMember(String role) {
        return OWNER.equals(role) || MEMBER.equals(role);
    }

    /** 是否至少有只读权限 */
    public static boolean isViewer(String role) {
        return OWNER.equals(role) || MEMBER.equals(role) || VIEWER.equals(role);
    }
}
