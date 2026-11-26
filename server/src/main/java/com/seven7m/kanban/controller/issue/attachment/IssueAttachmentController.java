package com.seven7m.kanban.controller.issue.attachment;

import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * 工作项附件：登记元信息、真实文件上传、删除、下载鉴权。
 *
 * <p>写要求 issue:edit，读要求 issue:view。文件名与落盘路径经校验，
 * 避免用 ../../ 之类的方式越出上传目录；下载走 /api/uploads/**，由 UploadsAuthInterceptor 二次鉴权。
 */
@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
public class IssueAttachmentController {

    private final IssueAttachmentService attachmentService;

    @PreAuthorize("@projectAuth.hasIssue(#issueId, 'issue:view')")
    @GetMapping("/{issueId}/attachments")
    public R<List<AttachmentVO>> list(@PathVariable UUID issueId) {
        return R.ok(attachmentService.list(issueId));
    }

    /** 真实文件上传：保存文件并登记附件记录（含可访问 url） */
    @PreAuthorize("@projectAuth.hasIssue(#issueId, 'issue:edit')")
    @PostMapping("/{issueId}/attachments/upload")
    public R<AttachmentVO> upload(@PathVariable UUID issueId, @RequestParam("file") MultipartFile file) {
        return R.ok(attachmentService.upload(issueId, file));
    }

    /** 兼容旧客户端：仅登记附件元信息，新客户端请使用 upload */
    @PreAuthorize("@projectAuth.hasIssue(#issueId, 'issue:edit')")
    @PostMapping("/{issueId}/attachments")
    public R<AttachmentVO> add(@PathVariable UUID issueId, @Valid @RequestBody AddAttachmentRequest body) {
        return R.ok(attachmentService.add(issueId, body.getFileName(), body.getContentType(), body.getFileSize()));
    }

    @PreAuthorize("@projectAuth.hasIssue(#issueId, 'issue:edit')")
    @DeleteMapping("/{issueId}/attachments/{attachmentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID attachmentId) {
        attachmentService.delete(issueId, attachmentId);
        return R.ok();
    }
}
