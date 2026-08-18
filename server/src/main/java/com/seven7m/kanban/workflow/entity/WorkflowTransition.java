package com.seven7m.kanban.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("workflow_transition")
public class WorkflowTransition {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID workflowId;
    private UUID fromStatusId;
    private UUID toStatusId;
    private String name;
    private LocalDateTime createdAt;
}
