package com.seven7m.kanban.entity.issue.comment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("issue_comment")
public class IssueComment {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID issueId;
    /** 隔离维度：TASK / REQUIREMENT / BUG，防止跨表串数据 */
    private String category;
    private UUID userId;
    private UUID parentId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
