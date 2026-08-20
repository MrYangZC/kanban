package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class LinkVO {
    private UUID id;
    private UUID issueId;
    private UUID targetIssueId;
    private String relationType;
    private String targetKey;
    private String targetTitle;
    private LocalDateTime createdAt;
}
