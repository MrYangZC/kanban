package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.dto.workflow.WorkflowTransitionVO;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /** 项目默认工作流的流转规则（看板拖拽蓝/红提示、前端预校验） */
    @GetMapping("/{projectId}/transitions")
    public R<List<WorkflowTransitionVO>> transitions(@PathVariable UUID projectId) {
        return R.ok(workflowService.listTransitions(projectId));
    }
}
