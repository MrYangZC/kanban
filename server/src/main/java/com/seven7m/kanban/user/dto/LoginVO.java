package com.seven7m.kanban.user.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class LoginVO {

    private UUID id;
    private String username;
    private String displayName;
    private String token;
}
