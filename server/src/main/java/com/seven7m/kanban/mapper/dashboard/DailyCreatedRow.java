package com.seven7m.kanban.mapper.dashboard;

import lombok.Data;

/**
 * 燃尽图「每日新建数」的查询投影（按 created_at 归日）。
 *
 * <p>与「每日完成」分开建模，原因见 {@link DailyCompletedRow}。
 */
@Data
public class DailyCreatedRow {

    /** yyyy-MM-dd */
    private String date;

    /** 当日新建数 */
    private Long created;
}
