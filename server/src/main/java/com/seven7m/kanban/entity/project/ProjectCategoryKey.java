package com.seven7m.kanban.entity.project;

/**
 * 项目分类筛选键。
 * all：全部项目（当前用户为成员且 status=ACTIVE）
 * managed：我管理的（当前用户角色含 OWNER/ADMIN/PM 且 status=ACTIVE）
 * participated：我参与的（当前用户为成员且 status=ACTIVE，与 all 等价，保留语义区分）
 * favorited：我收藏的（project_favorite 含当前用户且 status=ACTIVE）
 * archived：已归档（当前用户为成员且 status=ARCHIVED）
 */
public enum ProjectCategoryKey {

    ALL,
    MANAGED,
    PARTICIPATED,
    FAVORITED,
    ARCHIVED;

    /**
     * 字符串转枚举，null 或未知值返回 ALL（向后兼容，不传 category 时行为不变）。
     */
    public static ProjectCategoryKey of(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return ProjectCategoryKey.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}