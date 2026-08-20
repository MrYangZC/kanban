package com.seven7m.kanban.issue.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.entity.IssueComment;
import com.seven7m.kanban.issue.service.IssueCommentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class IssueCommentController {

    private final IssueCommentService commentService;

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER,
            com.seven7m.kanban.project.ProjectRole.ADMIN,
            com.seven7m.kanban.project.ProjectRole.PM,
            com.seven7m.kanban.project.ProjectRole.DEV,
            com.seven7m.kanban.project.ProjectRole.QA,
            com.seven7m.kanban.project.ProjectRole.MEMBER,
            com.seven7m.kanban.project.ProjectRole.VIEWER})
    @GetMapping("/{issueId}/comments")
    public R<List<IssueComment>> list(@PathVariable UUID issueId) {
        return R.ok(commentService.list(issueId));
    }

    @ProjectRole
    @PostMapping("/{issueId}/comments")
    public R<IssueComment> add(@PathVariable UUID issueId, @RequestBody Body body) {
        return R.ok(commentService.add(issueId, body.getUserId(), body.getContent(), body.getParentId()));
    }

    @ProjectRole
    @PutMapping("/{issueId}/comments/{commentId}")
    public R<IssueComment> update(@PathVariable UUID issueId, @PathVariable UUID commentId, @RequestBody Body body) {
        return R.ok(commentService.update(issueId, commentId, body.getUserId(), body.getContent()));
    }

    @ProjectRole
    @DeleteMapping("/{issueId}/comments/{commentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID commentId, @RequestParam UUID userId) {
        commentService.delete(issueId, commentId, userId);
        return R.ok();
    }

    @Data
    public static class Body {
        private UUID userId;
        private String content;
        private UUID parentId;
    }
}
