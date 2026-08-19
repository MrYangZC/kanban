package com.seven7m.kanban.announcement.dto;

import lombok.Data;

@Data
public class UpdateAnnouncementRequest {
    private String title;
    private String content;
    private Integer pinned;
}