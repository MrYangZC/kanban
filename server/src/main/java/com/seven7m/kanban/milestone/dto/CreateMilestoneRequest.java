package com.seven7m.kanban.milestone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateMilestoneRequest {
    @NotBlank(message = "里程碑名称必填")
    private String name;
    private String description;
    private LocalDate dueDate;
}