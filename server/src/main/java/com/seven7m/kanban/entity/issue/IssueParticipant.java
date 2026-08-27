package com.seven7m.kanban.entity.issue;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 问题参与者关联（issue_id ↔ user_id），替代原 issue.participants JSONB 字段。
 */
@Getter
@Setter
@TableName("issue_participant")
public class IssueParticipant {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID issueId;

    /** 隔离维度：TASK / REQUIREMENT / BUG，防止跨表串数据 */
    private String category;

    private UUID userId;

    private LocalDateTime createdAt;
}
