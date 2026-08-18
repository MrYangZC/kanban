package com.seven7m.kanban.issuestatus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目级状态实体（原文档第9节 workflow_status 重命名）。
 * Issue.status_id 直接引用本表。
 */
@Getter
@Setter
@TableName("issue_status")
public class IssueStatus {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    private String name;
    private String code;
    private String category;
    private String color;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
