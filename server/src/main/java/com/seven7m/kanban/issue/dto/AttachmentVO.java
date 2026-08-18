package com.seven7m.kanban.issue.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AttachmentVO {
    private UUID id;
    private UUID issueId;
    private String fileName;
    private String storageKey;
    private String contentType;
    private Long fileSize;
    private UUID uploadedBy;
    private String uploaderName;
    private LocalDateTime createdAt;
}
