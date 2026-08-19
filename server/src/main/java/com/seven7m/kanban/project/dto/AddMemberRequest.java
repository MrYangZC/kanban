package com.seven7m.kanban.project.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AddMemberRequest {

    @NotNull
    private UUID userId;

    /** 角色集合（RBAC：一人可多角色），默认 [MEMBER] */
    @NotEmpty
    private List<String> roles = List.of("MEMBER");
}
