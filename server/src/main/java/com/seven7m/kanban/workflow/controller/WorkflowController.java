package com.seven7m.kanban.workflow.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.workflow.dto.WorkflowTransitionVO;
import com.seven7m.kanban.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /** 项目默认工作流的流转规则（看板拖拽蓝/红提示、前端预校验） */
    @GetMapping("/v1/projects/{projectId}/transitions")
    public R<List<WorkflowTransitionVO>> transitions(@PathVariable UUID projectId) {
        return R.ok(workflowService.listTransitions(projectId));
    }
}
