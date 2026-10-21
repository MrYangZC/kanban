package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.workflow.WorkflowTransitionVO;
import com.seven7m.kanban.service.board.BoardService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final BoardService boardService;

    /**
     * 项目指定类别工作流的流转规则（看板拖拽蓝/红提示、前端预校验）。
     * category=TASK/REQUIREMENT/BUG，缺省 BUG（兼容旧调用）。
     * 属项目内数据，需具备工作项查看权限，避免任意登录用户遍历他项目的工作流配置。
     */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{projectId}/transitions")
    public R<List<WorkflowTransitionVO>> transitions(@PathVariable UUID projectId,
                                                     @RequestParam(required = false) String category) {
        ItemCategory cat = boardService.resolveCategory(category, ItemCategory.BUG);
        return R.ok(workflowService.listTransitions(projectId, cat));
    }
}
