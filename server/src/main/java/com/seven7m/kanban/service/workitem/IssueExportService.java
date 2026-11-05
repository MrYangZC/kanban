package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作项导出（CSV）。
 *
 * <p>对应一直存在却从未落地的权限码 {@code issue:export}：此前 RBAC 里能勾选，但没有任何接口使用它，
 * 属于"假权限"。这里给出一个真实用途，并沿用列表页的同一套筛选口径（scope / keyword / 类型）。
 */
@Service
@RequiredArgsConstructor
public class IssueExportService {

    /** 单次导出上限：与 /issues/filter 的单表上限一致，避免一次导出把内存打满 */
    private static final int EXPORT_LIMIT = 1000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WorkItemRouter workItemRouter;
    private final IssueStatusMapper issueStatusMapper;
    private final ProjectMapper projectMapper;

    /** 导出结果：文件名 + UTF-8(带 BOM) 的 CSV 字节 + 是否被截断 */
    public record ExportFile(String filename, byte[] content, boolean truncated, long total) {
    }

    /**
     * @param types 类型编码，逗号分隔；为空表示不按类型过滤（导出全部工作项）
     */
    public ExportFile export(UUID projectId, String types, String scope, String keyword) {
        IssueFilterRequest req = new IssueFilterRequest();
        req.setProjectId(projectId);
        req.setScope(scope);
        req.setKeyword(keyword);
        List<String> codes = splitCodes(types);
        if (!codes.isEmpty()) {
            req.setIssueTypeCodes(codes);
        }
        req.setSortField("issueKey");
        req.setSortOrder("asc");
        req.setPage(1);
        req.setSize(EXPORT_LIMIT);

        PageResult<IssueVO> page = workItemRouter.listByFilter(req);
        List<IssueVO> rows = page.getList() == null ? List.of() : page.getList();

        Map<UUID, String> statusNames = loadStatusNames(rows);

        StringBuilder csv = new StringBuilder("\uFEFF"); // BOM：让 Excel 正确识别 UTF-8
        csv.append("编号,类型,标题,状态,优先级,严重度,负责人,创建人,标签,迭代,截止日期,创建时间,更新时间\r\n");
        for (IssueVO row : rows) {
            csv.append(csv(row.getIssueKey())).append(',')
                    .append(csv(row.getIssueTypeName())).append(',')
                    .append(csv(row.getTitle())).append(',')
                    .append(csv(statusNames.getOrDefault(row.getStatusId(), row.getStatusCode()))).append(',')
                    .append(csv(row.getPriority())).append(',')
                    .append(csv(row.getSeverity())).append(',')
                    .append(csv(row.getAssigneeName())).append(',')
                    .append(csv(row.getReporterName())).append(',')
                    .append(csv(labels(row))).append(',')
                    .append(csv(row.getIteration())).append(',')
                    .append(csv(row.getDueDate() == null ? null : row.getDueDate().toString())).append(',')
                    .append(csv(dateTime(row.getCreatedAt()))).append(',')
                    .append(csv(dateTime(row.getUpdatedAt())))
                    .append("\r\n");
        }

        Project project = projectMapper.selectById(projectId);
        String key = project == null ? "issues" : project.getProjectKey();
        String filename = key + "-issues-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".csv";

        // 超过单次上限时明确告知：此前是静默截断，用户会误以为导出了全量
        long total = page.getTotal();
        boolean truncated = total > rows.size();
        if (truncated) {
            csv.append(csv("【已截断】共 " + total + " 条，本次仅导出前 " + rows.size()
                    + " 条；请缩小筛选范围后分批导出")).append("\r\n");
        }
        return new ExportFile(filename, csv.toString().getBytes(StandardCharsets.UTF_8), truncated, total);
    }

    /** 一次性批量取状态名（CSV 里展示中文状态，而不是 REQ_TODO 这种 code） */
    private Map<UUID, String> loadStatusNames(List<IssueVO> rows) {
        Set<UUID> statusIds = rows.stream()
                .map(IssueVO::getStatusId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (statusIds.isEmpty()) {
            return Map.of();
        }
        return issueStatusMapper.selectBatchIds(statusIds).stream()
                .collect(Collectors.toMap(IssueStatus::getId, IssueStatus::getName));
    }

    private static List<String> splitCodes(String types) {
        if (types == null || types.isBlank()) {
            return List.of();
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static String labels(IssueVO row) {
        if (row.getLabels() == null || row.getLabels().isEmpty()) {
            return "";
        }
        return row.getLabels().stream()
                .map(l -> l.getName() == null ? "" : l.getName())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" / "));
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
    }

    /** CSV 字段转义：统一加引号并转义内部引号，避免逗号/换行破坏列结构 */
    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
