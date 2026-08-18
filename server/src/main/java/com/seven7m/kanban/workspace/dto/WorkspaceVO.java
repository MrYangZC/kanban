package com.seven7m.kanban.workspace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkspaceVO {

    private UUID id;
    private String name;
    private String workspaceKey;
    private String description;
    private String avatarUrl;
    private LocalDateTime createdAt;
}
