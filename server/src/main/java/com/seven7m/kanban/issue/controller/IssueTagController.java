package com.seven7m.kanban.issue.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.dto.LabelVO;
import com.seven7m.kanban.issue.service.IssueTagService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class IssueTagController {

    private final IssueTagService tagService;

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER,
            com.seven7m.kanban.project.ProjectRole.ADMIN,
            com.seven7m.kanban.project.ProjectRole.PM,
            com.seven7m.kanban.project.ProjectRole.DEV,
            com.seven7m.kanban.project.ProjectRole.QA,
            com.seven7m.kanban.project.ProjectRole.MEMBER,
            com.seven7m.kanban.project.ProjectRole.VIEWER})
    @GetMapping("/{issueId}/labels")
    public R<List<LabelVO>> list(@PathVariable UUID issueId) {
        return R.ok(tagService.list(issueId));
    }

    @ProjectRole
    @PostMapping("/{issueId}/labels")
    public R<LabelVO> add(@PathVariable UUID issueId, @RequestBody Body body) {
        return R.ok(tagService.add(issueId, body.getLabelId()));
    }

    @ProjectRole
    @DeleteMapping("/{issueId}/labels/{labelId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID labelId) {
        tagService.delete(issueId, labelId);
        return R.ok();
    }

    @Data
    public static class Body {
        private UUID labelId;
    }
}
