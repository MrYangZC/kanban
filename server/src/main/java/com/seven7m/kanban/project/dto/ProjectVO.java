package com.seven7m.kanban.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

import com.seven7m.kanban.project.dto.ProjectStats;

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
}
