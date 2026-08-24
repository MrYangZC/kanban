package com.seven7m.kanban.controller.issue.tag;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.AddTagRequest;
import com.seven7m.kanban.dto.issue.LabelVO;
import com.seven7m.kanban.service.issue.tag.IssueTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class IssueTagController {

    private final IssueTagService tagService;

    /** 项目下标签（供打标选择） */
    @GetMapping("/projects/{projectId}/tags")
    public R<List<LabelVO>> projectTags(@PathVariable UUID projectId) {
        return R.ok(tagService.listForProject(projectId));
    }

    /** 某 issue 已关联标签 */
    @GetMapping("/issues/{issueId}/tags")
    public R<List<LabelVO>> issueTags(@PathVariable UUID issueId) {
        return R.ok(tagService.listForIssue(issueId));
    }

    @ProjectRoleAnnotation(Permission.PROJECT_LABEL)
    @PostMapping("/issues/{issueId}/tags")
    public R<Void> add(@PathVariable UUID issueId, @RequestBody AddTagRequest body) {
        tagService.addToIssue(body.getProjectId(), issueId, body.getName(), body.getColor());
        return R.ok();
    }

    @ProjectRoleAnnotation(Permission.PROJECT_LABEL)
    @DeleteMapping("/issues/{issueId}/tags/{labelId}")
    public R<Void> remove(@PathVariable UUID issueId, @PathVariable UUID labelId) {
        tagService.removeFromIssue(issueId, labelId);
        return R.ok();
    }

    /** 全局标签库（供标签管理界面选择） */
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
