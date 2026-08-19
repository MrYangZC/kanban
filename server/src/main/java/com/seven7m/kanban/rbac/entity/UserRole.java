package com.seven7m.kanban.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户-角色关联（多对多）。用户在同一项目可挂多个角色。
 */
@Getter
@Setter
@TableName("user_role")
public class UserRole {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID userId;

    private UUID roleId;

    private UUID projectId;

    private LocalDateTime createdAt;
}
