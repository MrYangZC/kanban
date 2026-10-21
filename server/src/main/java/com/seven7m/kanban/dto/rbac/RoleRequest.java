package com.seven7m.kanban.dto.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增/编辑请求体。
 *
 * 与 Role 实体的区别：只保留客户端允许提交的字段，不含 id / createdBy / updatedBy
 * 等审计字段——直接使用实体接参会让调用方注入这些字段（mass assignment）。
 * 字段名与 Role 实体保持一致，因此既有前端的 JSON 无需改动。
 */
@Data
public class RoleRequest {

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64")
    private String name;

    @Size(max = 255, message = "角色描述长度不能超过 255")
    private String description;

    private Integer sortOrder;
}
