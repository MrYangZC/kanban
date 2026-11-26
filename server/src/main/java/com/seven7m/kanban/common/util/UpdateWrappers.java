package com.seven7m.kanban.common.util;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * UPDATE 语句里"把列置为 NULL"的唯一写法。
 *
 * <p>为什么需要这个工具类：{@code updateById} 会按字段策略跳过 null，实体上的"清空"写不进去；
 * 而 {@code set(column, null)} 会把 null 作为绑定参数下发，能否成功取决于驱动/数据库能否
 * 从上下文推断该参数类型（PostgreSQL 在部分位置会直接报
 * {@code could not determine data type of parameter}）。写 SQL 字面量 {@code NULL}
 * 则完全避开类型推断问题。
 *
 * <p>把它收在这里，业务代码里就不会再出现手写的 SQL 片段——列名仍由
 * {@code WorkItemColumns} 一类的常量提供，拼错由编译期兜住。
 */
public final class UpdateWrappers {

    private UpdateWrappers() {
    }

    /**
     * 在 UPDATE 语句中把某列置空。
     *
     * @param wrapper 目标 UPDATE 条件构造器
     * @param column  列名（请用常量，不要手写字符串）
     */
    public static void clearToNull(UpdateWrapper<?> wrapper, String column) {
        wrapper.setSql(column + " = NULL");
    }
}
