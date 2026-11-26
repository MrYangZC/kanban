package com.seven7m.kanban.controller.issue.link;

import org.springframework.security.access.prepost.PreAuthorize;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.AddLinkRequest;
import com.seven7m.kanban.dto.issue.LinkVO;
import com.seven7m.kanban.service.issue.link.IssueLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 工作项双向关联（blocks / relates / duplicates 等关系类型）。
 *
 * <p>写要求 issue:edit；关联是双向的，服务层会同时维护正反两条记录。
 */
@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
public class IssueLinkController {

    private final IssueLinkService linkService;

    @PreAuthorize("@projectAuth.hasIssue(#issueId, 'issue:view')")
    @GetMapping("/{issueId}/links")
    public R<List<LinkVO>> list(@PathVariable UUID issueId) {
        return R.ok(linkService.list(issueId));
    }

    @PreAuthorize("@projectAuth.hasIssue(#issueId, 'issue:edit')")
    @PostMapping("/{issueId}/links")
    public R<LinkVO> add(@PathVariable UUID issueId, @Valid @RequestBody AddLinkRequest body) {
        return R.ok(linkService.add(issueId, body.getTargetIssueId(), body.getRelationType()));
    }

    @PreAuthorize("@projectAuth.hasIssue(#issueId, 'issue:edit')")
    @DeleteMapping("/{issueId}/links/{linkId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID linkId) {
        linkService.delete(issueId, linkId);
        return R.ok();
    }
}
