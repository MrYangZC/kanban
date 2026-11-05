package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.workflow.SaveHandoffRequest;
import com.seven7m.kanban.dto.workflow.WorkflowColumnVO;
import com.seven7m.kanban.dto.workflow.WorkflowTransitionVO;
import com.seven7m.kanban.service.board.BoardService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 工作流查询与配置：状态节点、流转规则、看板列（含每列交接配置）、保存交接。
 *
 * <p>读要求 issue:view，写（保存交接）要求 project:workflow。
 * 状态节点与流转规则按工作项类别区分，调用方必须显式传 category。
 */
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

    /**
     * 工作流的全部列 + 每列交接配置。看板列头与详情页状态下拉共用。
     * category 缺省 TASK（与看板默认类别一致；该接口无历史调用方）。
     */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{projectId}/workflow-columns")
    public R<List<WorkflowColumnVO>> columns(@PathVariable UUID projectId,
                                            @RequestParam(required = false) String category) {
        ItemCategory cat = boardService.resolveCategory(category, ItemCategory.TASK);
        return R.ok(workflowService.columnsOf(projectId, cat));
    }

    /**
     * 保存某一列的交接配置。handoffType=NONE 即清除，保留原负责人。
     * 属项目流程配置，用 project:workflow 权限（与工作流设置同级别），不能只要求 issue:view。
     */
    @ProjectRoleAnnotation(Permission.PROJECT_WORKFLOW)
    @PutMapping("/{projectId}/workflow-columns/{statusId}/handoff")
    public R<WorkflowColumnVO> saveHandoff(@PathVariable UUID projectId,
                                          @PathVariable UUID statusId,
                                          @RequestParam(required = false) String category,
                                          @Valid @RequestBody SaveHandoffRequest req) {
        ItemCategory cat = boardService.resolveCategory(category, ItemCategory.TASK);
        return R.ok(workflowService.saveHandoff(projectId, cat, statusId, req, UserContext.getUserId()));
    }
}
