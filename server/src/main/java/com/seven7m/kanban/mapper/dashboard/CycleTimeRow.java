package com.seven7m.kanban.mapper.dashboard;

import lombok.Data;

/**
 * 度量页「周期时间」的查询投影：已完成工作项的平均/最长解决时长（小时）。
 *
 * <p>由 {@code DashboardMapper#cycleTime} 显式映射。
 */
@Data
public class CycleTimeRow {

    private Double avgHours;

    private Double maxHours;
}
