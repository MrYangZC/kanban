package com.seven7m.kanban.controller.issue;

import org.springframework.security.access.prepost.PreAuthorize;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.service.workitem.IssueExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 工作项导出。
 *
 * <p>路径挂在 /projects/{projectId} 下而不是 /issues/export：后者的字面量会与 /issues/{id} 争用同一段路径，
 * 且这里天然需要项目级权限解析。
 */
@RestController
@RequestMapping("/projects/{projectId}/issues")
@RequiredArgsConstructor
public class IssueExportController {

    private final IssueExportService issueExportService;

    /**
     * 导出当前筛选结果为 CSV（Excel 可直接打开）。
     *
     * <p>筛选条件用 {@code @ModelAttribute} 从 query 参数绑定到 {@link IssueFilterRequest}：
     * 与列表接口同一份 DTO，字段天然对齐（状态/优先级/严重度/标签/迭代/负责人/创建者/
     * 创建·更新·计划完成区间/逾期/scope/关键词），前端只需把同一份筛选参数搬过来。
     *
     * @param types 类型编码，逗号分隔（如 TASK,IMPROVEMENT）；为空导出全部
     */
    @PreAuthorize("@projectAuth.has(#projectId, 'issue:export')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID projectId,
                                         @RequestParam(required = false) String types,
                                         @ModelAttribute IssueFilterRequest filter) {
        IssueExportService.ExportFile file = issueExportService.export(projectId, types, filter);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build());
        // 给脚本/集成方一个可判定的信号，避免"以为导出了全量"
        headers.set("X-Export-Truncated", Boolean.toString(file.truncated()));
        headers.set("X-Export-Total", Long.toString(file.total()));
        headers.set("X-Export-Rows", Integer.toString(file.rows()));
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
