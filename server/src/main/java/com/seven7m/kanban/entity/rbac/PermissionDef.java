package com.seven7m.kanban.entity.rbac;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 权限定义字典（细粒度权限码，按模块分组）。
 */
@Getter
@Setter
@TableName("permission")
public class PermissionDef {

    @TableId
    private String code;

    private String name;

    private String description;

    private String module;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}