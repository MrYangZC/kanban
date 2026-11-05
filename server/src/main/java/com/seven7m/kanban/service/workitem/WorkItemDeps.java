package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.AbstractWorkItem;

import java.util.function.Supplier;

/**
 * 单个工作项类别所需的"身份 + 表特异性依赖"。
 *
 * <p>三个类别（任务/需求/缺陷）只有 5 个差异点：类别枚举、表 Mapper、
 * 编号生成器、实体工厂，其余依赖全部来自 {@link WorkItemSharedDeps}。
 * 把差异点集中成这一个值对象后，`TaskService` 之类的子类不再需要 14 个字段 + 17 个覆盖方法，
 * 新增一个跨类别依赖也只需改 `WorkItemSharedDeps` 一处。
 */
public record WorkItemDeps<T extends AbstractWorkItem>(
        ItemCategory category,
        BaseMapper<T> mapper,
        Supplier<String> nextIssueNumber,
        Supplier<T> newEntity,
        WorkItemSharedDeps shared) {
}
