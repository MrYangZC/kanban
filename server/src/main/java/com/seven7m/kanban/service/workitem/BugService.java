package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.Bug;
import com.seven7m.kanban.mapper.workitem.BugMapper;
import org.springframework.stereotype.Service;

/**
 * 缺陷（bug 表）的工作项服务。
 *
 * <p>差异点只有 4 个：类别、表 Mapper、编号生成器、实体工厂；其余逻辑全部在
 * {@link BaseWorkItemService}，共享依赖在 {@link WorkItemSharedDeps}。
 */
@Service
public class BugService extends BaseWorkItemService<Bug> {

    public BugService(BugMapper bugMapper, WorkItemSharedDeps shared) {
        super(new WorkItemDeps<>(ItemCategory.BUG, bugMapper, bugMapper::nextIssueNumber, Bug::new, shared));
    }
}
