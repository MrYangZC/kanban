package com.seven7m.kanban.dto.project;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 项目更新请求。所有字段均可选，仅覆盖非空字段。
 */
@Data
public class UpdateProjectRequest {

    /** 与 project.name 的 varchar(200) 对齐 */
    @Size(max = 200, message = "项目名称不能超过 200 个字符")
    private String name;

    private String description;

    private String avatarUrl;

    private String status;

    private UUID leadUserId;
}
