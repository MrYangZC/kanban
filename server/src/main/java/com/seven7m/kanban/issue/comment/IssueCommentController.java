package com.seven7m.kanban.issue.comment;

import com.seven7m.kanban.common.domain.R;
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

    @PostMapping("/{issueId}/comments")
    public R<IssueActivityVO> add(@PathVariable UUID issueId, @RequestBody CommentBody body) {
        return R.ok(commentService.addComment(issueId, body.getContent()));
    }

    @DeleteMapping("/{issueId}/comments/{commentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID commentId) {
        commentService.deleteComment(issueId, commentId);
        return R.ok();
    }

    @Data
    public static class CommentBody {
        private String content;
    }
}
