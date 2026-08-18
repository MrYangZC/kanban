package com.seven7m.kanban.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWorkspaceRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String workspaceKey;

    private String description;
}
