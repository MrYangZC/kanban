package com.seven7m.kanban.dto.project;

import jakarta.validation.constraints.Pattern;
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

    /**
     * 项目状态，仅允许 ACTIVE / ARCHIVED。
     * 此前是自由字符串：写入任意值后项目会从「全部/我管理的/已归档」所有列表里消失
     * （列表只按这两种状态过滤），只能改库找回。
     */
    @Pattern(regexp = "ACTIVE|ARCHIVED", message = "项目状态仅支持 ACTIVE 或 ARCHIVED")
    private String status;

    private UUID leadUserId;
}
