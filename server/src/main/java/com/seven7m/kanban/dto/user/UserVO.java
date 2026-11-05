package com.seven7m.kanban.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class UserVO {
    private UUID id;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private Boolean enabled;
    /**
     * 是否系统管理员（sys_user.is_admin）。
     * 前端用户管理页据此渲染"管理员"开关；与 PermissionService#isSuperAdmin 口径一致。
     *
     * <p>属性名不带 {@code is} 前缀（手册强制），下发给前端的 JSON 字段名仍是 {@code isAdmin}。
     */
    @JsonProperty("isAdmin")
    private Boolean admin;
}
