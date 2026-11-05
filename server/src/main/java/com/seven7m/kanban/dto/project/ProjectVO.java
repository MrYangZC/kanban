package com.seven7m.kanban.dto.project;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

import com.seven7m.kanban.dto.project.ProjectStats;

@Data
public class ProjectVO {

    private UUID id;
    private String projectKey;
    private String name;
    private String description;
    private String status;
    private UUID leadUserId;
    private LocalDateTime createdAt;

    private ProjectStats stats;

    /** 当前用户是否已收藏该项目（用于前端卡片展示收藏标记） */
    private boolean favorited;

    /**
     * 当前用户是否可编辑项目设置。
     * 由权限码 project:setting 判定（OWNER/ADMIN/PM），与后端 @ProjectRoleAnnotation 同源；
     * 不再用角色硬编码，避免"按钮能点、请求 403"。
     */
    private boolean canEdit;

    /** 当前用户是否可管理项目成员（权限码 project:member） */
    private boolean canManage;

    /** 当前用户是否可归档/取消归档项目（权限码 project:archive，种子数据仅 OWNER/ADMIN） */
    private boolean canArchive;

    /** 当前用户是否可删除项目（权限码 project:delete，种子数据仅 OWNER/ADMIN） */
    private boolean canDelete;
}
