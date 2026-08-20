package com.seven7m.kanban.issue.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.dto.LinkVO;
import com.seven7m.kanban.issue.service.IssueLinkService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class IssueLinkController {

    private final IssueLinkService linkService;

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER,
            com.seven7m.kanban.project.ProjectRole.ADMIN,
            com.seven7m.kanban.project.ProjectRole.PM,
            com.seven7m.kanban.project.ProjectRole.DEV,
            com.seven7m.kanban.project.ProjectRole.QA,
            com.seven7m.kanban.project.ProjectRole.MEMBER,
            com.seven7m.kanban.project.ProjectRole.VIEWER})
    @GetMapping("/{issueId}/links")
    public R<List<LinkVO>> list(@PathVariable UUID issueId) {
        return R.ok(linkService.list(issueId));
    }

    @ProjectRole
    @PostMapping("/{issueId}/links")
    public R<LinkVO> add(@PathVariable UUID issueId, @RequestBody LinkBody body) {
        return R.ok(linkService.add(issueId, body.getTargetIssueId(), body.getRelationType()));
    }

    @ProjectRole
    @DeleteMapping("/{issueId}/links/{linkId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID linkId) {
        linkService.delete(issueId, linkId);
        return R.ok();
    }

    @Data
    public static class LinkBody {
        private UUID targetIssueId;
        private String relationType;
    }
}
