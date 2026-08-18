package com.seven7m.kanban.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProjectMemberVO {

    private UUID id;
    private UUID projectId;
    private UUID userId;

    /** 展示用：来自 sys_user */
    private String username;
    private String displayName;
    private String avatarUrl;

    /** 角色：OWNER / MEMBER */
    private String role;

    private LocalDateTime createdAt;
}
