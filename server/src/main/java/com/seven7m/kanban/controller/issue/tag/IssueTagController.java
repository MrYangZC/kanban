package com.seven7m.kanban.controller.issue.tag;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.AddTagRequest;
import com.seven7m.kanban.dto.issue.LabelVO;
import com.seven7m.kanban.service.issue.tag.IssueTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 标签相关接口：工作项打/取消标签、全局标签库、项目选用标签。
 *
 * <p>两类权限：改工作项标签要 issue:edit，管理标签库与项目选用要 project:label。
 * 路由前缀为空是因为同一控制器下同时挂 /issues/{id}/tags、/labels、/projects/{id}/labels 三组路径。
 */
@RestController
@RequiredArgsConstructor
public class IssueTagController {

    private final IssueTagService tagService;

    /**
     * 项目下标签（供打标选择）。
     * 读接口用 ISSUE_VIEW：种子数据中 project:label 仅授予 OWNER/ADMIN/PM，
     * 若沿用该码，DEV/QA/MEMBER/VIEWER 会被 403，导致看板/列表/详情的标签静默消失。
     * 写接口（下方 add/remove/addProjectLabel/removeProjectLabel）仍要求 PROJECT_LABEL。
     */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/projects/{projectId}/tags")
    public R<List<LabelVO>> projectTags(@PathVariable UUID projectId) {
        return R.ok(tagService.listForProject(projectId));
    }

    /** 某 issue 已关联标签（项目上下文由 issueId 解析，权限口径同上） */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/issues/{issueId}/tags")
    public R<List<LabelVO>> issueTags(@PathVariable UUID issueId) {
        return R.ok(tagService.listForIssue(issueId));
    }

    @ProjectRoleAnnotation(Permission.PROJECT_LABEL)
    @PostMapping("/issues/{issueId}/tags")
    public R<Void> add(@PathVariable UUID issueId, @Valid @RequestBody AddTagRequest body) {
        tagService.addToIssue(body.getProjectId(), issueId, body.getName(), body.getColor());
        return R.ok();
    }

    @ProjectRoleAnnotation(Permission.PROJECT_LABEL)
    @DeleteMapping("/issues/{issueId}/tags/{labelId}")
    public R<Void> remove(@PathVariable UUID issueId, @PathVariable UUID labelId) {
        tagService.removeFromIssue(issueId, labelId);
        return R.ok();
    }

    /**
     * 全局标签库（供标签管理界面选择）。
     * 该路径不带 projectId/issueId，项目级权限注解无法解析上下文（会被判 403），
     * 故仅依赖 UserContextInterceptor 的登录态校验——全局标签字典不含项目敏感数据。
     */
    @GetMapping("/labels")
    public R<List<LabelVO>> globalTags() {
        return R.ok(tagService.listGlobal());
    }

    /** 项目选用标签 */
    @ProjectRoleAnnotation(Permission.PROJECT_LABEL)
    @PostMapping("/projects/{projectId}/labels/{labelId}")
    public R<Void> addProjectLabel(@PathVariable UUID projectId, @PathVariable UUID labelId) {
        tagService.addProjectLabel(projectId, labelId);
        return R.ok();
    }

    /** 项目取消选用标签 */
    @ProjectRoleAnnotation(Permission.PROJECT_LABEL)
    @DeleteMapping("/projects/{projectId}/labels/{labelId}")
    public R<Void> removeProjectLabel(@PathVariable UUID projectId, @PathVariable UUID labelId) {
        tagService.removeProjectLabel(projectId, labelId);
        return R.ok();
    }
}
