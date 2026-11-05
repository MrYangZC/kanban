package com.seven7m.kanban.controller.issue.comment;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.AddCommentRequest;
import com.seven7m.kanban.dto.issue.IssueActivityVO;
import com.seven7m.kanban.dto.issue.IssueTimelineVO;
import com.seven7m.kanban.service.issue.comment.IssueCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 工作项评论：分页查询、新增、删除。
 *
 * <p>新增要求 issue:comment；删除的「只有作者或管理员可删」在服务层判定（控制器只做权限码拦截）。
 */
@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
public class IssueCommentController {

    private final IssueCommentService commentService;

    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{issueId}/activities")
    public R<IssueTimelineVO> list(@PathVariable UUID issueId,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return R.ok(commentService.listActivities(issueId, page, size));
    }

    @ProjectRoleAnnotation(Permission.ISSUE_COMMENT)
    @PostMapping("/{issueId}/comments")
    public R<IssueActivityVO> add(@PathVariable UUID issueId, @Valid @RequestBody AddCommentRequest body) {
        return R.ok(commentService.addComment(issueId, body.getContent(), body.getParentId()));
    }

    @ProjectRoleAnnotation(Permission.ISSUE_COMMENT)
    @DeleteMapping("/{issueId}/comments/{commentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID commentId) {
        commentService.deleteComment(issueId, commentId);
        return R.ok();
    }
}
