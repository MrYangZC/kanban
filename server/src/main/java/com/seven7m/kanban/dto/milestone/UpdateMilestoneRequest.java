package com.seven7m.kanban.dto.milestone;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateMilestoneRequest {
    private String name;
    private String description;
    private LocalDate dueDate;
    private String status;
    private Integer progress;
}