package com.seven7m.kanban.dto.user;

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
    /** 不填则默认 admin123 */
    private String password;
    /** 是否系统管理员；不填为 false。此前 DTO 缺此字段，导致"新建管理员"只能改库 */
    private Boolean isAdmin;
}
