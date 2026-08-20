package com.seven7m.kanban.issue.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.entity.IssueAttachment;
import com.seven7m.kanban.issue.service.IssueAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class IssueAttachmentController {

    private final IssueAttachmentService attachmentService;

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER,
            com.seven7m.kanban.project.ProjectRole.ADMIN,
            com.seven7m.kanban.project.ProjectRole.PM,
            com.seven7m.kanban.project.ProjectRole.DEV,
            com.seven7m.kanban.project.ProjectRole.QA,
            com.seven7m.kanban.project.ProjectRole.MEMBER,
            com.seven7m.kanban.project.ProjectRole.VIEWER})
    @GetMapping("/{issueId}/attachments")
    public R<List<IssueAttachment>> list(@PathVariable UUID issueId) {
        return R.ok(attachmentService.list(issueId));
    }

    @ProjectRole
    @PostMapping("/{issueId}/attachments")
    public R<IssueAttachment> add(@PathVariable UUID issueId,
                                  @RequestParam String fileName,
                                  @RequestParam String storageKey,
                                  @RequestParam(required = false) String contentType,
                                  @RequestParam(required = false) Long fileSize,
                                  @RequestParam UUID uploadedBy) {
        return R.ok(attachmentService.add(issueId, fileName, storageKey, contentType, fileSize, uploadedBy));
    }

    @ProjectRole
    @DeleteMapping("/{issueId}/attachments/{attachmentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID attachmentId) {
        attachmentService.delete(issueId, attachmentId);
        return R.ok();
    }
}
