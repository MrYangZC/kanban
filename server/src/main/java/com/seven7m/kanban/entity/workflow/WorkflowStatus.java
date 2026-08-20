package com.seven7m.kanban.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Workflow<->Status 映射（原文档第11节）。决定状态归属某工作流及列内顺序。
 */
@Getter
@Setter
@TableName("workflow_status")
public class WorkflowStatus {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID workflowId;
    private UUID statusId;
    private Integer sortOrder;
}
