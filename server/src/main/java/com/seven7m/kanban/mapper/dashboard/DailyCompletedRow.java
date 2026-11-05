package com.seven7m.kanban.mapper.dashboard;

import lombok.Data;

/**
 * 燃尽图「每日完成数」的查询投影（按 done_at 归日）。
 *
 * <p>与「每日新建」分开建模而不是共用一个三字段类：`@Results` 里声明了不存在于结果集的列，
 * 运行时会直接抛 "column not found"，共用一个 Map/POJO 反而更容易埋雷。
 */
@Data
public class DailyCompletedRow {

    /** yyyy-MM-dd */
    private String date;

    /** 当日完成数 */
    private Long completed;
}
