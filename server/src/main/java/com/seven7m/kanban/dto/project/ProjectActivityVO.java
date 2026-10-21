package com.seven7m.kanban.dto.project;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProjectActivityVO {
    private UUID id;
    private UUID actorId;
    private String actorName;
    /** 操作人头像地址（/api/uploads/xxx），为空时前端回退首字头像 */
    private String actorAvatarUrl;
    private String action;
    private UUID issueId;
    private String issueNumber;
    private String issueTitle;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}