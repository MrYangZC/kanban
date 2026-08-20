package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateIssueRequest {

    @NotNull
    private UUID projectId;

    private UUID issueTypeId;

    @NotNull
    private UUID statusId;

    private UUID sprintId;

    @NotBlank
    private String title;

    private String description;
    private String priority;
    private UUID assigneeId;
    private UUID verifierId;
    private LocalDateTime dueDate;
    private String severity;
    private String module;
    private String fixVersion;
    private String source;
    private Integer archived;
}
