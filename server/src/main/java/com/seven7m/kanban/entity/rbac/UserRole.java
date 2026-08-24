package com.seven7m.kanban.entity.rbac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户-角色授权（多对多，按项目维度）。承载用户的角色授权边界；
 * 用户在同一项目可挂多个角色。区别于 {@code project_member}（仅成员名册，不承载授权）。
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
