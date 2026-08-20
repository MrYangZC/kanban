package com.seven7m.kanban.dto.user;

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
}
