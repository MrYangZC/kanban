package com.seven7m.kanban.user.dto;

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
}
