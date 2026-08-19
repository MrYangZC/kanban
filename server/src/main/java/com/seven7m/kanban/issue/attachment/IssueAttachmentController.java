package com.seven7m.kanban.issue.attachment;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.dto.AttachmentVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class IssueAttachmentController {

    private final IssueAttachmentService attachmentService;

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER,
            com.seven7m.kanban.project.ProjectRole.MEMBER,
            com.seven7m.kanban.project.ProjectRole.VIEWER})
    @GetMapping("/{issueId}/attachments")
    public R<List<AttachmentVO>> list(@PathVariable UUID issueId) {
        return R.ok(attachmentService.list(issueId));
    }

    /** 真实文件上传：保存文件并登记附件记录（含可访问 url） */
    @ProjectRole
    @PostMapping("/{issueId}/attachments/upload")
    public R<AttachmentVO> upload(@PathVariable UUID issueId, @RequestParam("file") MultipartFile file) {
        return R.ok(attachmentService.upload(issueId, file));
    }

    /** 兼容旧客户端：仅登记附件元信息，新客户端请使用 upload */
    @ProjectRole
    @PostMapping("/{issueId}/attachments")
    public R<AttachmentVO> add(@PathVariable UUID issueId, @RequestBody AttachmentBody body) {
        return R.ok(attachmentService.add(issueId, body.getFileName(), body.getContentType(), body.getFileSize()));
    }

    @ProjectRole
    @DeleteMapping("/{issueId}/attachments/{attachmentId}")
    public R<Void> delete(@PathVariable UUID issueId, @PathVariable UUID attachmentId) {
        attachmentService.delete(issueId, attachmentId);
        return R.ok();
    }

    @Data
    public static class AttachmentBody {
        private String fileName;
        private String contentType;
        private Long fileSize;
    }
}
