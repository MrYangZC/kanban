package com.seven7m.kanban.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
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

    /** 系统管理员标识：与项目级角色无关，仅此字段可进入系统管理后台 */
    private Boolean isAdmin;
}
