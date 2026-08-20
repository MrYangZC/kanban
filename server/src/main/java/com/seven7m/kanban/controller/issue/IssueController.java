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
import com.seven7m.kanban.service.issue.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @ProjectRoleAnnotation
    @PostMapping
    public R<IssueVO> create(@Valid @RequestBody CreateIssueRequest req) {
        return R.ok(issueService.create(req));
    }

    @ProjectRoleAnnotation(Permission.READ)
    @GetMapping("/{id}")
    public R<IssueVO> get(@PathVariable UUID id) {
        return R.ok(issueService.get(id));
    }

    @ProjectRoleAnnotation
    @PutMapping("/{id}")
    public R<IssueVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateIssueRequest req) {
        return R.ok(issueService.update(id, req));
    }

    /** 拖拽：状态 + 位置一次提交（自由流转，仅校验目标状态属于本项目） */
    @ProjectRoleAnnotation
    @PatchMapping("/{id}/position")
    public R<IssueVO> move(@PathVariable UUID id, @Valid @RequestBody MoveIssueRequest req) {
        return R.ok(issueService.move(id, req));
    }

    @ProjectRoleAnnotation
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID id) {
        issueService.delete(id);
        return R.ok();
    }

    /** 列表过滤：左侧过滤器 + 顶部筛选，返回分页 IssueVO（只读，项目成员含 VIEWER 可读） */
    @ProjectRoleAnnotation(Permission.READ)
    @PostMapping("/filter")
    public R<PageResult<IssueVO>> filter(@Valid @RequestBody IssueFilterRequest req) {
        return R.ok(issueService.listByFilter(req));
    }

    /** 获取某任务的子任务列表（只读） */
    @ProjectRoleAnnotation(Permission.READ)
    @GetMapping("/{id}/children")
    public R<List<IssueVO>> children(@PathVariable UUID id) {
        return R.ok(issueService.listChildren(id));
    }
}
