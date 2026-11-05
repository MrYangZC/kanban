package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.mapper.workitem.BugMapper;
import com.seven7m.kanban.mapper.workitem.RequirementMapper;
import com.seven7m.kanban.mapper.workitem.TaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 三个工作项 Service 的"差异点"守护测试。
 *
 * <p>这次把 14 个字段 + 17 个覆盖方法压成 {@link WorkItemDeps} 之后，最容易犯的错
 * 是把类别和 Mapper 接错（比如需求接成 taskMapper，或用 BUG 类别查 requirement 表）——
 * 这类错误编译期发现不了，表现是"某一类工作项串表/查不到"。
 */
class WorkItemServiceWiringTest {

    private final WorkItemSharedDeps shared = mock(WorkItemSharedDeps.class);

    @Test
    @DisplayName("TaskService：类别 TASK、Mapper 为 taskMapper、编号来自 task 表")
    void taskServiceIsWiredToTaskTable() {
        TaskMapper mapper = mock(TaskMapper.class);
        when(mapper.nextIssueNumber()).thenReturn("T-1");
        TaskService service = new TaskService(mapper, shared);

        assertThat(service.category()).isEqualTo(ItemCategory.TASK);
        assertThat(service.mapper()).isSameAs(mapper);
        assertThat(service.nextIssueNumber()).isEqualTo("T-1");
        assertThat(service.newEntityInstance()).isInstanceOf(com.seven7m.kanban.entity.workitem.Task.class);
    }

    @Test
    @DisplayName("RequirementService：类别 REQUIREMENT、Mapper 为 requirementMapper")
    void requirementServiceIsWiredToRequirementTable() {
        RequirementMapper mapper = mock(RequirementMapper.class);
        when(mapper.nextIssueNumber()).thenReturn("R-1");
        RequirementService service = new RequirementService(mapper, shared);

        assertThat(service.category()).isEqualTo(ItemCategory.REQUIREMENT);
        assertThat(service.mapper()).isSameAs(mapper);
        assertThat(service.nextIssueNumber()).isEqualTo("R-1");
        assertThat(service.newEntityInstance()).isInstanceOf(com.seven7m.kanban.entity.workitem.Requirement.class);
    }

    @Test
    @DisplayName("BugService：类别 BUG、Mapper 为 bugMapper")
    void bugServiceIsWiredToBugTable() {
        BugMapper mapper = mock(BugMapper.class);
        when(mapper.nextIssueNumber()).thenReturn("B-1");
        BugService service = new BugService(mapper, shared);

        assertThat(service.category()).isEqualTo(ItemCategory.BUG);
        assertThat(service.mapper()).isSameAs(mapper);
        assertThat(service.nextIssueNumber()).isEqualTo("B-1");
        assertThat(service.newEntityInstance()).isInstanceOf(com.seven7m.kanban.entity.workitem.Bug.class);
    }

    @Test
    @DisplayName("共享依赖来自同一实例：三个 Service 不会各自持有一份")
    void sharedDepsAreReusedAcrossServices() {
        TaskService task = new TaskService(mock(TaskMapper.class), shared);
        RequirementService requirement = new RequirementService(mock(RequirementMapper.class), shared);

        // 通过同一个 shared 实例取到的 mapper 必须是同一个（这里用 mock 的身份断言）
        assertThat(task.issueStatusMapper()).isSameAs(requirement.issueStatusMapper());
        assertThat(task.projectMapper()).isSameAs(requirement.projectMapper());
        assertThat(task.workflowService()).isSameAs(requirement.workflowService());
    }
}
