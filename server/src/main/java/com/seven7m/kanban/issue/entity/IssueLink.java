package com.seven7m.kanban.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("issue_relation")
public class IssueLink {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID issueId;
    private UUID targetIssueId;
    private String relationType;
    private LocalDateTime createdAt;
}
