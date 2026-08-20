package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class IssueActivityVO {
    private UUID id;
    private String type;       // COMMENT / STATUS_CHANGED / FIELD_CHANGED ...
    private UUID actorId;
    private String actorName;
    private UUID parentId;     // 评论所属父评论 id（嵌套回复），非评论类活动为 null
    private Integer deleted;   // 评论是否逻辑删除；前端据其渲染「已删除」占位
    private String content;    // 评论内容
    private String oldValue;   // 活动旧值（JSON 文本）
    private String newValue;   // 活动新值（JSON 文本）
    private LocalDateTime createdAt;
}
