package com.seven7m.kanban.entity.rbac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 角色 -> 操作权限 映射（全局化后按 role_id 关联）。
 */
@Getter
@Setter
@TableName("role_permission")
public class RolePermission {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID roleId;

    private String permissionCode;

    private LocalDateTime createdAt;
}
