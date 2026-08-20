package com.seven7m.kanban.dto.user;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String displayName;
    private String email;
    private String avatarUrl;
    private Boolean enabled;
    /** 仅填写时修改密码（bcrypt 加密） */
    private String password;
}
