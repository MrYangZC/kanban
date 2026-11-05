package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class IssueActivityVO {
    private UUID id;
    private String type;
    private UUID actorId;
    private String actorName;
    /** 操作人头像地址（/api/uploads/xxx），为空时前端回退首字头像 */
    private String actorAvatarUrl;
    private UUID parentId;
    private String content;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
    /**
     * 仅交接动态会出现：这条交接当前是否可以撤销。
     * 判定口径在后端（最后一条交接 + 接手人仍是当前负责人），前端只按标记显示按钮。
     */
    private Boolean revocable;
}
