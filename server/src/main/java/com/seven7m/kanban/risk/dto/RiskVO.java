package com.seven7m.kanban.risk.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class RiskVO {
    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private String level;
    private String status;
    private UUID ownerId;
    private String ownerName;
    private LocalDate dueDate;
    private String creatorName;
    private String createdAt;
    private String updatedAt;
}
