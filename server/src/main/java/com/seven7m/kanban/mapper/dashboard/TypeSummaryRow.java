package com.seven7m.kanban.mapper.dashboard;

import lombok.Data;

/**
 * 度量页「分类型概览」的查询投影：每个工作项类别一行，口径与 {@link DashboardSummaryRow} 一致。
 *
 * <p>由 {@code DashboardMapper#typeSummaries} 显式映射。
 */
@Data
public class TypeSummaryRow {

    /** REQUIREMENT / BUG / TASK */
    private String typeCode;

    private Long total;
    private Long todo;
    private Long inProgress;
    private Long done;
    private Long overdue;
}
