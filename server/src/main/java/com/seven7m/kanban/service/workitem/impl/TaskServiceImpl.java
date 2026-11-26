package com.seven7m.kanban.service.workitem.impl;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.Task;
import com.seven7m.kanban.mapper.workitem.TaskMapper;
import com.seven7m.kanban.service.workitem.TaskService;
import com.seven7m.kanban.service.workitem.WorkItemDeps;
import com.seven7m.kanban.service.workitem.WorkItemSharedDeps;
import org.springframework.stereotype.Service;

/**
 * 任务（task 表）的工作项服务实现。
 *
 * <p>差异点只有 4 个：类别、表 Mapper、编号生成器、实体工厂；其余逻辑全部在
 * {@link AbstractWorkItemService}，共享依赖在 {@link WorkItemSharedDeps}。
 */
@Service
public class TaskServiceImpl extends AbstractWorkItemService<Task> implements TaskService {

    public TaskServiceImpl(TaskMapper taskMapper, WorkItemSharedDeps shared) {
        super(new WorkItemDeps<>(ItemCategory.TASK, taskMapper, taskMapper::nextIssueNumber,
                Task::new, taskMapper::selectTailSortKey, shared));
    }
}
