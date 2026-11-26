package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.activity.ActivityValue;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.notification.NotificationService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 交接撤销决策的单元测试。
 *
 * <p>这块逻辑原先埋在 {@code BaseWorkItemService} 的 1100 行里、且一处测试都没有；
 * 抽成 {@link WorkItemHandoffService} 后可以只 mock 一条动态记录就把五条分支跑完。
 * 撤销是对"已经发生的误操作"的唯一补救手段，判断错了会覆盖别人的编辑，因此值得锁住。
 */
@ExtendWith(MockitoExtension.class)
class WorkItemHandoffServiceTest {

    private static final UUID ISSUE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID FROM_USER = UUID.randomUUID();
    private static final UUID TO_USER = UUID.randomUUID();
    private static final UUID SPRINT_OLD = UUID.randomUUID();
    private static final UUID SPRINT_NEW = UUID.randomUUID();

    @Mock
    private WorkflowService workflowService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private IssueTagMapper issueTagMapper;
    @Mock
    private IssueActivityMapper issueActivityMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SysUserMapper sysUserMapper;

    private WorkItemHandoffService service;

    @BeforeEach
    void setUp() {
        service = new WorkItemHandoffService(workflowService, projectMapper, issueTagMapper,
                issueActivityMapper, notificationService, sysUserMapper);
    }

    /** 构造一条交接动态；sprintId 传 null 表示"没有迭代"（编码后是空串） */
    private static IssueActivity handoffActivity(String action, UUID from, UUID to, UUID fromSprint, UUID toSprint) {
        IssueActivity act = new IssueActivity();
        act.setId(UUID.randomUUID());
        act.setIssueId(ISSUE_ID);
        act.setCategory(ItemCategory.TASK.name());
        act.setAction(action);
        act.setOldValue(ActivityValue.userWithSprint("原负责人", from, fromSprint));
        act.setNewValue(ActivityValue.userWithSprint("接手人", to, toSprint));
        act.setCreatedAt(LocalDateTime.now());
        return act;
    }

    private static WorkItemHandoffService.Context ctx(UUID currentAssignee, UUID currentSprint) {
        return new WorkItemHandoffService.Context(ISSUE_ID, PROJECT_ID, ItemCategory.TASK,
                "1001", "示例任务", currentAssignee, currentSprint);
    }

    @Test
    @DisplayName("没有交接记录时拒绝撤销")
    void rejectsWhenNoHandoff() {
        lenient().when(issueActivityMapper.selectLatestHandoffActivity(any(), any(), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.planRevoke(ctx(TO_USER, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有可撤销的交接");
    }

    @Test
    @DisplayName("最后一条是「已撤销」时拒绝再撤（避免撤销变成可撤销项）")
    void rejectsWhenLastActionIsRevoke() {
        IssueActivity revoke = handoffActivity(WorkItemHandoffService.ACTION_HANDOFF_REVOKED,
                FROM_USER, TO_USER, null, null);
        lenient().when(issueActivityMapper.selectLatestHandoffActivity(any(), any(), any(), any())).thenReturn(revoke);

        assertThatThrownBy(() -> service.planRevoke(ctx(TO_USER, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有可撤销的交接");
    }

    @Test
    @DisplayName("负责人已被别人改过时拒绝撤销，不覆盖别人的编辑")
    void rejectsWhenAssigneeChangedAgain() {
        IssueActivity act = handoffActivity(WorkItemHandoffService.ACTION_ASSIGNEE_CHANGED,
                FROM_USER, TO_USER, null, null);
        lenient().when(issueActivityMapper.selectLatestHandoffActivity(any(), any(), any(), any())).thenReturn(act);

        UUID someoneElse = UUID.randomUUID();
        assertThatThrownBy(() -> service.planRevoke(ctx(someoneElse, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("负责人已被再次变更");
    }

    @Test
    @DisplayName("正常撤销：负责人退回，迭代仍是交接写入的那个值时一并退回")
    void restoresSprintWhenUnchangedSinceHandoff() {
        IssueActivity act = handoffActivity(WorkItemHandoffService.ACTION_ASSIGNEE_CHANGED,
                FROM_USER, TO_USER, SPRINT_OLD, SPRINT_NEW);
        lenient().when(issueActivityMapper.selectLatestHandoffActivity(any(), any(), any(), any())).thenReturn(act);

        WorkItemHandoffService.RevokePlan plan = service.planRevoke(ctx(TO_USER, SPRINT_NEW));

        assertThat(plan.fromUserId()).isEqualTo(FROM_USER);
        assertThat(plan.toUserId()).isEqualTo(TO_USER);
        assertThat(plan.sprintRestored()).isTrue();
        assertThat(plan.restoredSprintId()).isEqualTo(SPRINT_OLD);
    }

    @Test
    @DisplayName("迭代被改成别的值后只退负责人，不闷头把迭代退回去")
    void keepsSprintWhenChangedBySomeoneElse() {
        IssueActivity act = handoffActivity(WorkItemHandoffService.ACTION_ASSIGNEE_CHANGED,
                FROM_USER, TO_USER, SPRINT_OLD, SPRINT_NEW);
        lenient().when(issueActivityMapper.selectLatestHandoffActivity(any(), any(), any(), any())).thenReturn(act);

        WorkItemHandoffService.RevokePlan plan = service.planRevoke(ctx(TO_USER, UUID.randomUUID()));

        assertThat(plan.sprintRestored()).isFalse();
        assertThat(plan.restoredSprintId()).isNull();
    }

    @Test
    @DisplayName("交接前没有迭代时，撤销要把 sprint_id 置空（sprintRestored=true 且 id 为 null）")
    void clearsSprintWhenThereWasNoneBefore() {
        IssueActivity act = handoffActivity(WorkItemHandoffService.ACTION_ASSIGNEE_CHANGED,
                FROM_USER, TO_USER, null, SPRINT_NEW);
        lenient().when(issueActivityMapper.selectLatestHandoffActivity(any(), any(), any(), any())).thenReturn(act);

        WorkItemHandoffService.RevokePlan plan = service.planRevoke(ctx(TO_USER, SPRINT_NEW));

        assertThat(plan.sprintRestored()).isTrue();
        assertThat(plan.restoredSprintId()).isNull();
    }

    @Test
    @DisplayName("显式指定了负责人（且不同于当前负责人）时不触发交接")
    void doesNotResolveWhenAssigneeExplicitlyChosen() {
        WorkItemHandoffService.Context c = ctx(FROM_USER, null);

        WorkItemHandoffService.Decision decision = service.resolve(c, UUID.randomUUID(), "处理中",
                UUID.randomUUID());

        assertThat(decision).isNull();
    }

    @Test
    @DisplayName("状态 id 为空时不触发交接")
    void doesNotResolveWithoutTargetStatus() {
        assertThat(service.resolve(ctx(FROM_USER, null), null, null, null)).isNull();
    }
}
