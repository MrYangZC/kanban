package com.seven7m.kanban.workflow.dto;

import java.util.UUID;

/**
 * 工作流流转规则：fromStatus → toStatus 是否允许。
 * 同一对状态只会出现一条规则（workflow_transition 由项目初始化时写入）。
 */
public record WorkflowTransitionVO(UUID fromStatusId, UUID toStatusId, String name) {
}
