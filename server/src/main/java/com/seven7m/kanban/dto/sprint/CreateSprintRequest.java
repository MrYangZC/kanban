package com.seven7m.kanban.dto.sprint;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateSprintRequest {
    @NotBlank(message = "迭代名称必填")
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
}
