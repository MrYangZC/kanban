package com.seven7m.kanban.dto.project;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AddMemberRequest {

    @NotNull
    private UUID userId;

    /**
     * 角色集合（RBAC：一人可多角色）。
     *
     * <p>按手册要求 POJO 不设属性默认值，因此这里不预置 [MEMBER]：
     * 调用方必须显式传角色（前端 {@code addProjectMember} 始终传，缺省为 MEMBER）。
     */
    @NotEmpty(message = "至少指定一个角色")
    private List<String> roles;
}
