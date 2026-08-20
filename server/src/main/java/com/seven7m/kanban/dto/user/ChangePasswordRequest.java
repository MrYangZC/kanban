package com.seven7m.kanban.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 当前用户修改密码请求：需校验旧密码。
 */
@Data
public class ChangePasswordRequest {
    @NotBlank
    private String oldPassword;
    @NotBlank
    private String newPassword;
}