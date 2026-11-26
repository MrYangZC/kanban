package com.seven7m.kanban.controller.issue;

import org.springframework.security.access.prepost.PreAuthorize;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.BatchDeleteIssueRequest;
import com.seven7m.kanban.dto.issue.BatchDeleteResult;
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

    @PreAuthorize("@projectAuth.has(#req.projectId, 'issue:create')")
    @PostMapping
    public R<IssueVO> create(@Valid @RequestBody CreateIssueRequest req) {
        return R.ok(workItemRouter.create(req));
    }

    @PreAuthorize("@projectAuth.hasId(#id, 'issue:view')")
    @GetMapping("/{id}")
    public R<IssueVO> get(@PathVariable UUID id) {
        return R.ok(workItemRouter.getById(id));
    }

    @PreAuthorize("@projectAuth.hasId(#id, 'issue:edit')")
    @PutMapping("/{id}")
    public R<IssueVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateIssueRequest req) {
        return R.ok(workItemRouter.update(id, req));
    }

    /** 拖拽：状态 + 位置一次提交（自由流转，仅校验目标状态属于本项目） */
    @PreAuthorize("@projectAuth.hasId(#id, 'issue:edit')")
    @PatchMapping("/{id}/position")
    public R<IssueVO> move(@PathVariable UUID id, @Valid @RequestBody MoveIssueRequest req) {
        return R.ok(workItemRouter.move(id, req));
    }

    /**
     * 撤销最近一次交接：负责人退回交接前的人。
     * 与改负责人同一权限（能改负责人的人才能撤销交接，撤销不比改负责人更危险）。
     */
    @PreAuthorize("@projectAuth.hasId(#id, 'issue:edit')")
    @PostMapping("/{id}/handoff/revoke")
    public R<IssueVO> revokeHandoff(@PathVariable UUID id) {
        return R.ok(workItemRouter.revokeHandoff(id));
    }

    @PreAuthorize("@projectAuth.hasId(#id, 'issue:delete')")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID id) {
        workItemRouter.delete(id);
        return R.ok();
    }

    /**
     * 批量删除工作项。
     *
     * <p>权限走与单条删除相同的 {@code issue:delete}；项目由拦截器从请求体的 projectId 解析，
     * 服务端还会校验每个 id 确实属于该项目（否则"换个 projectId 就能删别人的工作项"）。
     * 返回 deleted/skipped/failed 三项计数，前端据此给出准确提示。
     */
    @PreAuthorize("@projectAuth.has(#req.projectId, 'issue:delete')")
    @PostMapping("/batch-delete")
    public R<BatchDeleteResult> batchDelete(@RequestBody @Valid BatchDeleteIssueRequest req) {
        return R.ok(workItemRouter.batchDelete(req.getProjectId(), req.getIds()));
    }

    /** 列表过滤：左侧过滤器 + 顶部筛选，返回分页 IssueVO（只读，项目成员含 VIEWER 可读） */
    @PreAuthorize("@projectAuth.has(#req.projectId, 'issue:view')")
    @PostMapping("/filter")
    public R<PageResult<IssueVO>> filter(@Valid @RequestBody IssueFilterRequest req) {
        return R.ok(workItemRouter.listByFilter(req));
    }

    /** 获取某任务的子任务列表（只读） */
    @PreAuthorize("@projectAuth.hasId(#id, 'issue:view')")
    @GetMapping("/{id}/children")
    public R<List<IssueVO>> children(@PathVariable UUID id) {
        return R.ok(workItemRouter.listChildren(id));
    }
}
