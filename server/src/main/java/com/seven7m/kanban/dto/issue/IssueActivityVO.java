package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class IssueActivityVO {
    private UUID id;
    private String type;
    private UUID actorId;
    private String actorName;
    private UUID parentId;
    private String content;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}
