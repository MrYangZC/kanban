package com.seven7m.kanban.mapper.dashboard;

import lombok.Data;

/**
 * 度量页「概览卡片」的查询投影（总数 / 待处理 / 处理中 / 已完成 / 逾期）。
 *
 * <p>由 {@code DashboardMapper#summary} 显式映射（见该方法上的 @Results）。
 * 属性一律用包装类型：手册要求 POJO 不依赖基本类型默认值，数据库侧 COUNT 为 NULL 时也便于发现。
 */
@Data
public class DashboardSummaryRow {

    private Long total;

    /** issue_status.category = TODO */
    private Long todo;

    /** category = IN_PROGRESS */
    private Long inProgress;

    /** category = DONE（含已关闭/已取消等终态） */
    private Long done;

    /** 有截止日、已过期且未完成 */
    private Long overdue;
}
