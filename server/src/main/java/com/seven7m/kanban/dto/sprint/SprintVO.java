package com.seven7m.kanban.dto.sprint;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class SprintVO {
    private UUID id;
    private UUID projectId;
    private String name;
    private String goal;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String creatorName;
    private String createdAt;
    private String updatedAt;
}
