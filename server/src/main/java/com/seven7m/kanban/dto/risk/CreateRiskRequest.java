package com.seven7m.kanban.dto.risk;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateRiskRequest {
    @NotBlank(message = "风险标题必填")
    private String title;
    private String description;
    private String level;
    private String status;
    private UUID ownerId;
    private LocalDate dueDate;
}
