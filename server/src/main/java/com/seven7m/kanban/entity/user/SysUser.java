package com.seven7m.kanban.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String passwordHash;
    private Boolean enabled;

    /**
     * 系统管理员标识：与项目级角色无关，仅此字段可进入系统管理后台。
     *
     * <p>属性名不带 {@code is} 前缀（手册强制：POJO 布尔属性禁止 is 前缀，否则部分框架
     * 反向解析会把属性名当成 "admin" 而取不到值）；数据库列仍按手册要求命名为 {@code is_admin}。
     */
    @TableField("is_admin")
    private Boolean admin;
}
