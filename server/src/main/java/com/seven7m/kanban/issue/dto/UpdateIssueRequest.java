package com.seven7m.kanban.issue.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UpdateIssueRequest {

    private String title;
    private String description;
    private String priority;
    private UUID issueTypeId;
    private UUID assigneeId;
    private UUID verifierId;
    private LocalDateTime dueDate;
    private String severity;
    private String module;
    private String fixVersion;
    private String source;
    private Integer archived;
    private UUID statusId;
}
