package com.seven7m.kanban.project;

/**
 * 项目状态枚举。
 * ACTIVE：正常项目（默认值，向后兼容现有字符串 "ACTIVE"）
 * ARCHIVED：已归档项目（不进入"全部项目"列表，仅在"已归档"分类展示）
 */
public enum ProjectStatus {

    ACTIVE,
    ARCHIVED;

    /**
     * 字符串转枚举，null 或未知值返回 ACTIVE（向后兼容历史脏数据）。
     */
    public static ProjectStatus of(String value) {
        if (value == null) {
            return ACTIVE;
        }
        try {
            return ProjectStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}