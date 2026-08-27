package com.seven7m.kanban.dto.announcement;

import lombok.Data;

@Data
public class UpdateAnnouncementRequest {
    private String title;
    private String content;
    private Boolean pinned;
}