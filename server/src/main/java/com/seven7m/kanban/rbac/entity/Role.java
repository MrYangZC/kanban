package com.seven7m.kanban.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 全局角色字典。
 */
@Getter
@Setter
@TableName("role")
public class Role extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 角色编码，如 OWNER / ADMIN / PM / DEV / QA / MEMBER / VIEWER */
    private String code;

    private String name;

    private String description;

    private Integer sortOrder;
}
