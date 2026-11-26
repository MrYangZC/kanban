package com.seven7m.kanban.service.workitem.impl;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.Requirement;
import com.seven7m.kanban.mapper.workitem.RequirementMapper;
import com.seven7m.kanban.service.workitem.RequirementService;
import com.seven7m.kanban.service.workitem.WorkItemDeps;
import com.seven7m.kanban.service.workitem.WorkItemSharedDeps;
import org.springframework.stereotype.Service;

/**
 * 需求（requirement 表）的工作项服务实现。差异点说明见 {@link TaskServiceImpl}。
 */
@Service
public class RequirementServiceImpl extends AbstractWorkItemService<Requirement> implements RequirementService {

    public RequirementServiceImpl(RequirementMapper requirementMapper, WorkItemSharedDeps shared) {
        super(new WorkItemDeps<>(ItemCategory.REQUIREMENT, requirementMapper,
                requirementMapper::nextIssueNumber, Requirement::new, shared));
    }
}
