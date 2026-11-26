package com.seven7m.kanban.service.workitem.impl;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.Bug;
import com.seven7m.kanban.mapper.workitem.BugMapper;
import com.seven7m.kanban.service.workitem.BugService;
import com.seven7m.kanban.service.workitem.WorkItemDeps;
import com.seven7m.kanban.service.workitem.WorkItemSharedDeps;
import org.springframework.stereotype.Service;

/**
 * 缺陷（bug 表）的工作项服务实现。差异点说明见 {@link TaskServiceImpl}。
 */
@Service
public class BugServiceImpl extends AbstractWorkItemService<Bug> implements BugService {

    public BugServiceImpl(BugMapper bugMapper, WorkItemSharedDeps shared) {
        super(new WorkItemDeps<>(ItemCategory.BUG, bugMapper, bugMapper::nextIssueNumber, Bug::new, shared));
    }
}
