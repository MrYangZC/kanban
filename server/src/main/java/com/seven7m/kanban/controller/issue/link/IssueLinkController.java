package com.seven7m.kanban.controller.issue.link;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.AddLinkRequest;
import com.seven7m.kanban.dto.issue.LinkVO;
import com.seven7m.kanban.service.issue.link.IssueLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
public class IssueLinkController {

    private final IssueLinkService linkService;

    @ProjectRoleAnnotation(Permission.READ)
    @GetMapping("/{issueId}/links")
    public R<List<LinkVO>> list(@PathVariable UUID issueId) {
        return R.ok(linkService.list(issueId));
    }

    @ProjectRoleAnnotation
    @PostMapping("/{issueId}/links")
    public R<LinkVO> add(@PathVariable UUID issueId, @RequestBody AddLinkRequest body) {
        return R.ok(linkService.add(issueId, body.getTargetIssueId(), body.getRelationType()));
    }

    @ProjectRoleAnnotation
    @DeleteMapping("/{issueId}/links/{linkId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID linkId) {
        linkService.delete(issueId, linkId);
        return R.ok();
    }
}
