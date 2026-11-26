package com.seven7m.kanban.service.workitem.impl;

import com.seven7m.kanban.common.constant.PageLimits;
import com.seven7m.kanban.common.domain.NameIndex;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.dto.issue.IssueExportFile;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.service.workitem.IssueExportService;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import com.seven7m.kanban.service.workitem.WorkItemSort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作项导出（CSV）实现。
 *
 * <p>对应一直存在却从未落地的权限码 {@code issue:export}：此前 RBAC 里能勾选，但没有任何接口使用它，
 * 属于"假权限"。这里给出一个真实用途，并沿用列表页的同一套筛选口径（scope / keyword / 类型）。
 */
@Service
@RequiredArgsConstructor
public class IssueExportServiceImpl implements IssueExportService {

    /** 单次导出上限：与 /issues/filter 的单表上限一致，避免一次导出把内存打满 */
    private static final int EXPORT_LIMIT = PageLimits.LIST_MAX_SIZE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** CSV 表头：导出模板与字段顺序的唯一来源 */
    private static final String CSV_HEADER =
            "编号,类型,标题,状态,优先级,严重度,负责人,创建人,标签,迭代,截止日期,创建时间,更新时间\r\n";

    private final WorkItemRouter workItemRouter;
    private final IssueStatusMapper issueStatusMapper;
    private final ProjectMapper projectMapper;

    /**
     * 导出当前筛选结果。
     *
     * <p>筛选条件整包复用列表用的 {@link IssueFilterRequest}（scope / 关键词 / 状态 / 优先级 /
     * 严重度 / 标签 / 迭代 / 负责人 / 创建者 / 三个时间区间 / 逾期），
     * 只由这里覆盖「项目、类型码、排序、分页/上限」这四类导出专属参数。
     * 这样「列表里看到的」与「导出的」用的是同一套 where 条件，不会出现两套口径。
     *
     * @param types 类型编码，逗号分隔；为空表示不按类型过滤（导出全部工作项）
     * @param filter 列表页当前的筛选条件（可为 null，等价于不筛）
     */
    @Override
    public IssueExportFile export(UUID projectId, String types, IssueFilterRequest filter) {
        IssueFilterRequest req = filter == null ? new IssueFilterRequest() : filter;
        // 项目以路径参数为准：不允许通过 query 里的 projectId 越权到别的项目
        req.setProjectId(projectId);
        List<String> codes = splitCodes(types);
        if (!codes.isEmpty()) {
            req.setIssueTypeCodes(codes);
        } else {
            req.setIssueTypeCodes(null);
        }
        req.setSortField("issueKey");
        req.setSortOrder(WorkItemSort.ASC);
        req.setPage(1);
        req.setSize(EXPORT_LIMIT);

        PageResult<IssueVO> page = workItemRouter.listByFilter(req);
        List<IssueVO> rows = page.getList() == null ? List.of() : page.getList();

        NameIndex<UUID> statusNames = loadStatusNames(rows);

        StringBuilder csv = new StringBuilder("\uFEFF"); // BOM：让 Excel 正确识别 UTF-8
        csv.append(CSV_HEADER);
        for (IssueVO row : rows) {
            String statusName = statusNames.nameOf(row.getStatusId());
            csv.append(csv(row.getIssueKey())).append(',')
                    .append(csv(row.getIssueTypeName())).append(',')
                    .append(csv(row.getTitle())).append(',')
                    .append(csv(statusName == null ? row.getStatusCode() : statusName)).append(',')
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
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String filename = key + "-issues-" + stamp + ".csv";

        // 超过单次上限时明确告知：此前是静默截断，用户会误以为导出了全量
        long total = page.getTotal();
        boolean truncated = total > rows.size();
        if (truncated) {
            csv.append(csv("【已截断】共 " + total + " 条，本次仅导出前 " + rows.size()
                    + " 条；请缩小筛选范围后分批导出")).append("\r\n");
        }
        return new IssueExportFile(filename, csv.toString().getBytes(StandardCharsets.UTF_8),
                truncated, total, rows.size());
    }

    /** 一次性批量取状态名（CSV 里展示中文状态，而不是 REQ_TODO 这种 code） */
    private NameIndex<UUID> loadStatusNames(List<IssueVO> rows) {
        Set<UUID> statusIds = rows.stream()
                .map(IssueVO::getStatusId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (statusIds.isEmpty()) {
            return NameIndex.empty();
        }
        return NameIndex.of(issueStatusMapper.selectBatchIds(statusIds).stream()
                .collect(Collectors.toMap(IssueStatus::getId, IssueStatus::getName)));
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
