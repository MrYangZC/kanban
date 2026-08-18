package com.seven7m.kanban.issue.attachment;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.issue.dto.AttachmentVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class IssueAttachmentController {

    private final IssueAttachmentService attachmentService;

    @GetMapping("/{issueId}/attachments")
    public R<List<AttachmentVO>> list(@PathVariable UUID issueId) {
        return R.ok(attachmentService.list(issueId));
    }

    @PostMapping("/{issueId}/attachments")
    public R<AttachmentVO> add(@PathVariable UUID issueId, @RequestBody AttachmentBody body) {
        return R.ok(attachmentService.add(issueId, body.getFileName(), body.getContentType(), body.getFileSize()));
    }

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
