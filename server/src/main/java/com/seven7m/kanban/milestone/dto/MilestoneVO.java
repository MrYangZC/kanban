package com.seven7m.kanban.milestone.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class MilestoneVO {
    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private LocalDate dueDate;
    private String status;
    private Integer progress;
    private String creatorName;
    private String createdAt;
    private String updatedAt;
}