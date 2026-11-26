package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.dto.issue.IssueExportFile;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;

import java.util.UUID;

/**
 * 工作项导出接口（CSV）。
 *
 * <p>导出口径与列表页共用 {@link IssueFilterRequest}，只覆盖项目、类型码、排序与上限，
 * 保证"列表里看到的"和"导出的"是同一套 where 条件。
 */
public interface IssueExportService {

    /**
     * 导出当前筛选结果。
     *
     * @param projectId 项目 id（以路径参数为准，忽略筛选条件里的同名参数，避免越权）
     * @param types     类型编码，逗号分隔；为空表示不按类型过滤
     * @param filter    列表页当前筛选条件（可为 null，等价于不筛）
     * @return 文件名 + UTF-8(带 BOM) 的 CSV 字节 + 是否截断 + 总命中数 + 实际行数；结构见 {@link IssueExportFile}
     */
    IssueExportFile export(UUID projectId, String types, IssueFilterRequest filter);
}
