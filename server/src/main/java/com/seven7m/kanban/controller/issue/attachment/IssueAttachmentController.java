package com.seven7m.kanban.controller.issue.attachment;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.AddAttachmentRequest;
import com.seven7m.kanban.dto.issue.AttachmentVO;
import com.seven7m.kanban.service.issue.attachment.IssueAttachmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
public class IssueAttachmentController {

    private final IssueAttachmentService attachmentService;

    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{issueId}/attachments")
    public R<List<AttachmentVO>> list(@PathVariable UUID issueId) {
        return R.ok(attachmentService.list(issueId));
    }

    /** 真实文件上传：保存文件并登记附件记录（含可访问 url） */
    @ProjectRoleAnnotation(Permission.ISSUE_EDIT)
    @PostMapping("/{issueId}/attachments/upload")
    public R<AttachmentVO> upload(@PathVariable UUID issueId, @RequestParam("file") MultipartFile file) {
        return R.ok(attachmentService.upload(issueId, file));
    }

    /** 兼容旧客户端：仅登记附件元信息，新客户端请使用 upload */
    @ProjectRoleAnnotation(Permission.ISSUE_EDIT)
    @PostMapping("/{issueId}/attachments")
    public R<AttachmentVO> add(@PathVariable UUID issueId, @Valid @RequestBody AddAttachmentRequest body) {
        return R.ok(attachmentService.add(issueId, body.getFileName(), body.getContentType(), body.getFileSize()));
    }

    @ProjectRoleAnnotation(Permission.ISSUE_EDIT)
    @DeleteMapping("/{issueId}/attachments/{attachmentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID attachmentId) {
        attachmentService.delete(issueId, attachmentId);
        return R.ok();
    }
}
