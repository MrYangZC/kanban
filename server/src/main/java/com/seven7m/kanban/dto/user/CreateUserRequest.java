package com.seven7m.kanban.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String displayName;
    private String email;
    private String avatarUrl;
    /** 初始密码，必填（为空时服务端拒绝，不再回退到固定弱口令） */
    private String password;
    /**
     * 是否系统管理员；不填为 false。
     * 属性名按手册去掉 {@code is} 前缀，JSON 字段名保持 {@code isAdmin} 以兼容前端。
     */
    @JsonProperty("isAdmin")
    private Boolean admin;
}
