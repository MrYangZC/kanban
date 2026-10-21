package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 新增标签请求。约束与 label 表列宽一致（name varchar(50) NOT NULL、color varchar(32)）。
 */
@Data
public class AddTagRequest {

    /** 所属项目 id */
    private UUID projectId;

    /** 标签名 */
    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名长度不能超过 50")
    private String name;

    /** 标签颜色（形如 #RRGGBB） */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "颜色格式应为 #RRGGBB")
    private String color;
}
