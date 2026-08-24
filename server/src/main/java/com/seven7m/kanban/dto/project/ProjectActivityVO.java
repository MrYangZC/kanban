package com.seven7m.kanban.dto.project;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProjectActivityVO {
    private UUID id;
    private UUID actorId;
    private String actorName;
    private String action;
    private UUID issueId;
    private String issueNumber;
    private String issueTitle;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}