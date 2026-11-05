package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.Task;
import com.seven7m.kanban.mapper.workitem.TaskMapper;
import org.springframework.stereotype.Service;

/**
 * 任务（task 表）的工作项服务。
 *
 * <p>差异点只有 4 个：类别、表 Mapper、编号生成器、实体工厂；其余逻辑全部在
 * {@link BaseWorkItemService}，共享依赖在 {@link WorkItemSharedDeps}。
 */
@Service
public class TaskService extends BaseWorkItemService<Task> {

    public TaskService(TaskMapper taskMapper, WorkItemSharedDeps shared) {
        super(new WorkItemDeps<>(ItemCategory.TASK, taskMapper, taskMapper::nextIssueNumber, Task::new, shared));
    }
}
