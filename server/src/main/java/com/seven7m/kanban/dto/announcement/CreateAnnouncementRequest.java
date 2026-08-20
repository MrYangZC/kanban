package com.seven7m.kanban.dto.announcement;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAnnouncementRequest {
    @NotBlank(message = "标题必填")
    private String title;
    private String content;
    private Integer pinned;
}