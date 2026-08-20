package com.seven7m.kanban.dto.issue;

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
    private String url;
    private UUID uploadedBy;
    private String uploaderName;
    private LocalDateTime createdAt;
}
