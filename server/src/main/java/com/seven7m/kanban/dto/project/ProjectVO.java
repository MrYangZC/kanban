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

    /** 当前用户是否可编辑该项目设置（基于角色判定，OWNER/ADMIN/PM 可编辑） */
    private boolean canEdit;

    /** 当前用户是否可管理该项目成员与设置（基于角色判定，OWNER/ADMIN/PM 可管理） */
    private boolean canManage;
}
