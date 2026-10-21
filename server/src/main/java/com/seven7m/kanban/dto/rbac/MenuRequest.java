package com.seven7m.kanban.dto.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 菜单新增/编辑请求体。
 *
 * 与 Menu 实体的区别：只保留客户端允许提交的字段，不含 id / createdBy / updatedBy
 * 等审计字段——直接使用实体接参会让调用方注入这些字段（mass assignment）。
 * 字段名与 Menu 实体保持一致，因此既有前端的 JSON 无需改动。
 */
@Data
public class MenuRequest {

    @NotBlank(message = "菜单编码不能为空")
    @Size(max = 64, message = "菜单编码长度不能超过 64")
    private String code;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过 64")
    private String name;

    private UUID parentId;

    /** 菜单类型，当前仅 MENU（保留字段以兼容历史数据） */
    @Size(max = 32, message = "菜单类型长度不能超过 32")
    private String type;

    private Integer sortOrder;
}
