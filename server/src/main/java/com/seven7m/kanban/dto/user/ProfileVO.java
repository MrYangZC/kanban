package com.seven7m.kanban.dto.user;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 个人中心视图：当前登录用户的完整资料。
 */
@Data
public class ProfileVO {
    private UUID id;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private Boolean enabled;
    private LocalDateTime createdAt;
}