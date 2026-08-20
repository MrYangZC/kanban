package com.seven7m.kanban.entity.rbac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 角色-菜单关联（多对多）。
 */
@Getter
@Setter
@TableName("role_menu")
public class RoleMenu {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID roleId;

    private UUID menuId;

    private LocalDateTime createdAt;
}
