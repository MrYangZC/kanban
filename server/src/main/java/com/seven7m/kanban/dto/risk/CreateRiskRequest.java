package com.seven7m.kanban.dto.risk;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateRiskRequest {
    private String title;
    private String description;
    private String level;
    private String status;
    private UUID ownerId;
    private LocalDate dueDate;
}
