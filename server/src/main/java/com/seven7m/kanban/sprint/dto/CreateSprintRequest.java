package com.seven7m.kanban.sprint.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateSprintRequest {
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
}
