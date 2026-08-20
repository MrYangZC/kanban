package com.seven7m.kanban.dto.user;

import lombok.Data;

/**
 * 当前用户修改个人资料请求（不允许改 username/enabled）。
 */
@Data
public class UpdateProfileRequest {
    private String displayName;
    private String email;
    private String avatarUrl;
}