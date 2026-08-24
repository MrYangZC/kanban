package com.seven7m.kanban.entity.issue;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 问题抄送关联（issue_id ↔ user_id），替代原 issue.cc_users JSONB 字段。
 */
@Getter
@Setter
@TableName("issue_cc")
public class IssueCc {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID issueId;

    private UUID userId;

    private LocalDateTime createdAt;
}
