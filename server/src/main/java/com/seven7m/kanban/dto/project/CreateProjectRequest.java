package com.seven7m.kanban.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateProjectRequest {

    @NotBlank
    private String projectKey;

    /** 与 project.name 的 varchar(200) 对齐，避免超长值直达数据库约束 */
    @NotBlank
    @Size(max = 200, message = "项目名称不能超过 200 个字符")
    private String name;

    private String description;

    private UUID leadUserId;
}
