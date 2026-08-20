package com.seven7m.kanban.issue.tag;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.dto.LabelVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class IssueTagController {

    private final IssueTagService tagService;

    /** 项目下标签（供打标选择） */
    @GetMapping("/v1/projects/{projectId}/tags")
    public R<List<LabelVO>> projectTags(@PathVariable UUID projectId) {
        return R.ok(tagService.listForProject(projectId));
    }

    /** 某 issue 已关联标签 */
    @GetMapping("/v1/issues/{issueId}/tags")
    public R<List<LabelVO>> issueTags(@PathVariable UUID issueId) {
        return R.ok(tagService.listForIssue(issueId));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PostMapping("/v1/issues/{issueId}/tags")
    public R<Void> add(@PathVariable UUID issueId, @RequestBody TagBody body) {
        tagService.addToIssue(body.getProjectId(), issueId, body.getName(), body.getColor());
        return R.ok();
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @DeleteMapping("/v1/issues/{issueId}/tags/{labelId}")
    public R<Void> remove(@PathVariable UUID issueId, @PathVariable UUID labelId) {
        tagService.removeFromIssue(issueId, labelId);
        return R.ok();
    }

    @Data
    public static class TagBody {
        private UUID projectId;
        private String name;
        private String color;
    }
}
