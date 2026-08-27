package com.seven7m.kanban.dto.announcement;

import lombok.Data;

import java.util.UUID;

@Data
public class AnnouncementVO {
    private UUID id;
    private UUID projectId;
    private String title;
    private String content;
    private Boolean pinned;
    private String authorName;
    private String createdAt;
    private String updatedAt;
}