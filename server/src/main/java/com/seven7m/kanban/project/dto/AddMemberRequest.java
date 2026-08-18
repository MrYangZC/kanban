package com.seven7m.kanban.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddMemberRequest {

    @NotNull
    private UUID userId;

    /** 角色：OWNER / MEMBER，默认 MEMBER */
    @NotBlank
    private String role = "MEMBER";
}
