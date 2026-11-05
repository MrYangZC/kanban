package com.seven7m.kanban.controller.issue;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.MoveIssueRequest;
import com.seven7m.kanban.dto.issue.UpdateIssueRequest;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 工作项主入口：创建、更新、拖拽/流转、详情、列表筛选、删除。
 *
 * <p>工作项已从单表拆分为 task/requirement/bug，本控制器不直接碰表，
 * 统一转给 WorkItemRouter 按类别路由；写操作按动作分别要求
 * issue:create / issue:edit / issue:status / issue:assign / issue:delete，
 * 状态与负责人变更不再笼统算作「编辑」。
 */
@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
public class IssueController {

    private final WorkItemRouter workItemRouter;

    @ProjectRoleAnnotation(Permission.ISSUE_CREATE)
    @PostMapping
    public R<IssueVO> create(@Valid @RequestBody CreateIssueRequest req) {
        return R.ok(workItemRouter.create(req));
    }

    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{id}")
    public R<IssueVO> get(@PathVariable UUID id) {
        return R.ok(workItemRouter.getById(id));
    }

    @ProjectRoleAnnotation(Permission.ISSUE_EDIT)
    @PutMapping("/{id}")
    public R<IssueVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateIssueRequest req) {
        return R.ok(workItemRouter.update(id, req));
    }

    /** 拖拽：状态 + 位置一次提交（自由流转，仅校验目标状态属于本项目） */
    @ProjectRoleAnnotation(Permission.ISSUE_EDIT)
    @PatchMapping("/{id}/position")
    public R<IssueVO> move(@PathVariable UUID id, @Valid @RequestBody MoveIssueRequest req) {
        return R.ok(workItemRouter.move(id, req));
    }

    /**
     * 撤销最近一次交接：负责人退回交接前的人。
     * 与改负责人同一权限（能改负责人的人才能撤销交接，撤销不比改负责人更危险）。
     */
    @ProjectRoleAnnotation(Permission.ISSUE_EDIT)
    @PostMapping("/{id}/handoff/revoke")
    public R<IssueVO> revokeHandoff(@PathVariable UUID id) {
        return R.ok(workItemRouter.revokeHandoff(id));
    }

    @ProjectRoleAnnotation(Permission.ISSUE_DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID id) {
        workItemRouter.delete(id);
        return R.ok();
    }

    /** 列表过滤：左侧过滤器 + 顶部筛选，返回分页 IssueVO（只读，项目成员含 VIEWER 可读） */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @PostMapping("/filter")
    public R<PageResult<IssueVO>> filter(@Valid @RequestBody IssueFilterRequest req) {
        return R.ok(workItemRouter.listByFilter(req));
    }

    /** 获取某任务的子任务列表（只读） */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{id}/children")
    public R<List<IssueVO>> children(@PathVariable UUID id) {
        return R.ok(workItemRouter.listChildren(id));
    }
}
