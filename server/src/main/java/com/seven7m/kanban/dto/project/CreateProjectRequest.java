package com.seven7m.kanban.dto.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateProjectRequest {

    @NotBlank
    private String projectKey;

    @NotBlank
    private String name;

    private String description;

    private UUID leadUserId;
}
