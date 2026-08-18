package com.seven7m.kanban.issue.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.dto.CreateIssueRequest;
import com.seven7m.kanban.issue.dto.IssueFilterRequest;
import com.seven7m.kanban.issue.dto.IssueVO;
import com.seven7m.kanban.issue.dto.MoveIssueRequest;
import com.seven7m.kanban.issue.dto.UpdateIssueRequest;
import com.seven7m.kanban.issue.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @ProjectRole
    @PostMapping
    public R<IssueVO> create(@Valid @RequestBody CreateIssueRequest req) {
        return R.ok(issueService.create(req));
    }

    @GetMapping("/{id}")
    public R<IssueVO> get(@PathVariable UUID id) {
        return R.ok(issueService.get(id));
    }

    @ProjectRole
    @PutMapping("/{id}")
    public R<IssueVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateIssueRequest req) {
        return R.ok(issueService.update(id, req));
    }

    /** 拖拽：状态 + 位置一次提交（自由流转，仅校验目标状态属于本项目） */
    @ProjectRole
    @PatchMapping("/{id}/position")
    public R<IssueVO> move(@PathVariable UUID id, @Valid @RequestBody MoveIssueRequest req) {
        return R.ok(issueService.move(id, req));
    }

    @ProjectRole
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID id) {
        issueService.delete(id);
        return R.ok();
    }

    /** 列表过滤：左侧过滤器 + 顶部筛选，返回分页 IssueVO（只读接口，无需角色注解） */
    @PostMapping("/filter")
    public R<PageResult<IssueVO>> filter(@Valid @RequestBody IssueFilterRequest req) {
        return R.ok(issueService.listByFilter(req));
    }

    /** 获取某任务的子任务列表（只读） */
    @GetMapping("/{id}/children")
    public R<List<IssueVO>> children(@PathVariable UUID id) {
        return R.ok(issueService.listChildren(id));
    }
}
