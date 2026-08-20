package com.seven7m.kanban.issue.comment;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.dto.IssueActivityVO;
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

    @GetMapping("/{issueId}/activities")
    public R<List<IssueActivityVO>> list(@PathVariable UUID issueId) {
        return R.ok(commentService.listActivities(issueId));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM, com.seven7m.kanban.project.ProjectRole.DEV, com.seven7m.kanban.project.ProjectRole.QA, com.seven7m.kanban.project.ProjectRole.MEMBER})
    @PostMapping("/{issueId}/comments")
    public R<IssueActivityVO> add(@PathVariable UUID issueId, @RequestBody CommentBody body) {
        return R.ok(commentService.addComment(issueId, body.getContent(), body.getParentId()));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM, com.seven7m.kanban.project.ProjectRole.DEV, com.seven7m.kanban.project.ProjectRole.QA, com.seven7m.kanban.project.ProjectRole.MEMBER})
    @DeleteMapping("/{issueId}/comments/{commentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID commentId) {
        commentService.deleteComment(issueId, commentId);
        return R.ok();
    }

    @Data
    public static class CommentBody {
        private String content;
        private UUID parentId;   // 嵌套回复：父评论 id，顶楼评论为 null
    }
}
