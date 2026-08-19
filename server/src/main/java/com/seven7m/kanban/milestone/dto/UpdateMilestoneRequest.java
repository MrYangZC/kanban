package com.seven7m.kanban.milestone.dto;

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