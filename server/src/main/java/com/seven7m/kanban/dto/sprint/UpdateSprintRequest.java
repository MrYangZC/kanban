package com.seven7m.kanban.dto.sprint;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateSprintRequest {
    @Size(min = 1, message = "迭代名称不能为空")
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
}
