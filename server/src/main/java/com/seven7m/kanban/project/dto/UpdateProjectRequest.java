package com.seven7m.kanban.project.dto;

import lombok.Data;

import java.util.UUID;

/**
 * 项目更新请求。所有字段均可选，仅覆盖非空字段。
 */
@Data
public class UpdateProjectRequest {

    private String name;

    private String description;

    private String avatarUrl;

    private String status;

    private UUID leadUserId;
}
