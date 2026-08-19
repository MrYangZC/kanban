package com.seven7m.kanban.announcement.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AnnouncementVO {
    private UUID id;
    private UUID projectId;
    private String title;
    private String content;
    private Integer pinned;
    private String authorName;
    private String createdAt;
    private String updatedAt;
}