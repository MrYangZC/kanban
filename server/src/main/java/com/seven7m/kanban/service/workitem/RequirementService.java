package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.Requirement;
import com.seven7m.kanban.mapper.workitem.RequirementMapper;
import org.springframework.stereotype.Service;

/**
 * 需求（requirement 表）的工作项服务。
 *
 * <p>差异点只有 4 个：类别、表 Mapper、编号生成器、实体工厂；其余逻辑全部在
 * {@link BaseWorkItemService}，共享依赖在 {@link WorkItemSharedDeps}。
 */
@Service
public class RequirementService extends BaseWorkItemService<Requirement> {

    public RequirementService(RequirementMapper requirementMapper, WorkItemSharedDeps shared) {
        super(new WorkItemDeps<>(ItemCategory.REQUIREMENT, requirementMapper,
                requirementMapper::nextIssueNumber, Requirement::new, shared));
    }
}
