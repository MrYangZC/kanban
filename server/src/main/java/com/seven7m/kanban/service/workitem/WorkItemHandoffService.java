package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.activity.ActivityValue;
import com.seven7m.kanban.common.constant.ActivityActions;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.util.Uuids;
import com.seven7m.kanban.dto.workflow.HandoffTarget;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.notification.NotificationService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 工作项交接（handoff）的解析与副作用。
 *
 * <p>从 {@code AbstractWorkItemService} 抽出来的一块：交接只依赖「项目的列配置 + 负责人 + 迭代 + 标签」，
 * 与具体是哪张表（task/requirement/bug）无关，因此只需要一个 {@link Context} 值对象就能工作，
 * 不再需要泛型实体。
 *
 * <p>职责边界：
 * <ul>
 *   <li>本类负责<b>决策与副作用</b>：解析目标列的交接配置、写动态（issue_activity）、落点迭代/标签、发站内通知；</li>
 *   <li>调用方负责<b>持久化</b>：把负责人/迭代写进实体与 UPDATE 语句，保证与状态变更同一次落库。</li>
 * </ul>
 *
 * <p>两个容易踩的语义，代码里都保留了原实现并加了注释：
 * ①「显式指定负责人」要排除「等于当前负责人」（详情页整表单保存会把未变字段带上）；
 * ②撤销交接只认最后一条交接动态，且接手人必须仍是当前负责人。
 */
@Component
@RequiredArgsConstructor
public class WorkItemHandoffService {

    /** 交接动态：负责人由 A 变为 B */
    public static final String ACTION_ASSIGNEE_CHANGED = ActivityActions.ASSIGNEE_CHANGED;
    /** 撤销交接：负责人由 B 退回 A（与上面区分，避免撤销本身又成为可撤销项） */
    public static final String ACTION_HANDOFF_REVOKED = ActivityActions.HANDOFF_REVOKED;
    /** 交接落点：工作项被放进某个迭代（单列「迭代」是工作项的强属性，换掉要留痕） */
    public static final String ACTION_SPRINT_CHANGED = ActivityActions.SPRINT_CHANGED;
    /** 交接落点：工作项被补上某个标签（只在真正新增时记一条，已经有就静默） */
    public static final String ACTION_LABEL_ADDED = ActivityActions.LABEL_ADDED;

    private final WorkflowService workflowService;
    private final ProjectMapper projectMapper;
    private final IssueTagMapper issueTagMapper;
    private final IssueActivityMapper issueActivityMapper;
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;

    /**
     * 交接涉及的上下文。传值而不是传实体，是为了让这块逻辑与 task/requirement/bug 三张表解耦。
     *
     * @param assigneeId 流转前的负责人，null 表示未分配
     * @param sprintId   流转前的迭代，null 表示没有迭代
     * @param issueNumber 工作项编号（通知正文用）
     * @param title       工作项标题（通知正文用）
     */
    public record Context(UUID issueId, UUID projectId, ItemCategory category,
                          String issueNumber, String title,
                          UUID assigneeId, UUID sprintId) {
    }

    /**
     * 一次流转的交接计划；仅在确实解析出「换人」时才有值。
     *
     * @param fromSprintId 交接前的迭代，撤销时据此把迭代退回去；null 表示本来没有迭代
     * @param sprintId     落点迭代，null 表示本列不规定落点
     * @param labelId      落点标签，null 表示本列不规定落点
     */
    public record Decision(UUID fromUserId, UUID toUserId, String toUserName,
                           String statusName, String note,
                           UUID sprintId, String sprintName,
                           UUID labelId, String labelName,
                           UUID fromSprintId) {
    }

    /**
     * 撤销计划。
     *
     * @param fromUserId      交接前的负责人（撤销要退回的人），null 表示交接前本来没有负责人
     * @param toUserId        交接后的负责人（当前负责人），用于校验「撤销是否还成立」
     * @param sprintRestored  是否要写回迭代；为 true 时 {@code restoredSprintId == null} 表示置空
     * @param restoredSprintId 要退回的迭代
     */
    public record RevokePlan(UUID fromUserId, UUID toUserId, boolean sprintRestored, UUID restoredSprintId) {
    }

    /**
     * 解析目标列的交接。用户主动改了负责人时跳过——刚选的人不该被列配置覆盖。
     *
     * <p>注意「显式指定」的判定要排除「等于当前负责人」的情形：详情页保存时会把未变的
     * 负责人一并提交，若把这种请求当作显式指派，从详情页改状态就永远不触发交接。
     *
     * @return null 表示本次流转不交接：无配置、类型 NONE、解析不出人、或与当前负责人相同
     */
    public Decision resolve(Context ctx, UUID toStatusId, String toStatusName, UUID explicitAssigneeId) {
        if (toStatusId == null) {
            return null;
        }
        if (explicitAssigneeId != null && !explicitAssigneeId.equals(ctx.assigneeId())) {
            return null;
        }
        // 只在真正流转时查项目，静态更新（改标题/优先级）不付这次查询
        Project project = projectMapper.selectById(ctx.projectId());
        HandoffTarget target = workflowService.resolveHandoffTarget(
                ctx.projectId(), ctx.category(), toStatusId, project);
        if (target == null || target.userId().equals(ctx.assigneeId())) {
            return null;
        }
        return new Decision(ctx.assigneeId(), target.userId(), target.userName(),
                toStatusName, target.note(),
                target.sprintId(), target.sprintName(), target.labelId(), target.labelName(),
                ctx.sprintId());
    }

    /**
     * 交接副作用：动态 + 落点 + 站内通知。调用方应让它与负责人改写同处一个事务，
     * 任一失败整体回滚，不会留下「人转了但没通知」的中间态。
     *
     * @param applySprint 迭代落点是否由本次交接写入（用户在请求里显式指定了迭代时为 false，
     *                    与「显式指定负责人不覆盖」同一原则）
     * @param applyLabel  标签落点是否由本次交接写入（请求里带了 labelIds 时为 false：
     *                    那是全量替换，交接再插一次会被下一行代码删掉，属于白做工）
     */
    public void record(Context ctx, Decision decision, boolean applySprint, boolean applyLabel) {
        String fromName = userNameOf(decision.fromUserId());
        // 首尾两条都带上「当时的迭代」：撤销要靠它们判断交接到底动没动迭代，
        // 以及这一刻迭代是否还停在交接写入的那个值（被别人改过就不能退回去覆盖）
        UUID toSprintId = applySprint && decision.sprintId() != null
                ? decision.sprintId()
                : decision.fromSprintId();
        recordActivity(ctx, ACTION_ASSIGNEE_CHANGED,
                ActivityValue.userWithSprint(fromName, decision.fromUserId(), decision.fromSprintId()),
                ActivityValue.userWithSprint(decision.toUserName(), decision.toUserId(), toSprintId));

        boolean sprintApplied = applySprint && decision.sprintId() != null;
        if (sprintApplied) {
            String fromSprintName = decision.fromSprintId() == null
                    ? null
                    : workflowService.sprintNameOf(decision.fromSprintId());
            recordActivity(ctx, ACTION_SPRINT_CHANGED,
                    ActivityValue.field("sprint", fromSprintName),
                    ActivityValue.field("sprint", decision.sprintName()));
        }
        boolean labelApplied = false;
        if (applyLabel && decision.labelId() != null) {
            Integer existing = issueTagMapper.countIssueLabel(
                    ctx.issueId(), decision.labelId(), ctx.category().name());
            // 已经有这个标签就不写：重复 insert 违反唯一约束，白写一条「打上标签」的动态也没意义
            if (existing == null || existing == 0) {
                issueTagMapper.insertIssueLabel(ctx.issueId(), decision.labelId(), ctx.category().name());
                recordActivity(ctx, ACTION_LABEL_ADDED, null,
                        ActivityValue.field("label", decision.labelName()));
                labelApplied = true;
            }
        }

        // 通知里的落点按「实际发生了什么」写：标签本来就有的那种情况不该骗接手人说打上了
        notificationService.notifyHandoff(decision.toUserId(), ctx.projectId(), ctx.issueId(),
                ctx.category().name(), ctx.issueNumber(), ctx.title(), decision.statusName(),
                decision.note(),
                landingText(sprintApplied ? decision.sprintName() : null,
                        labelApplied ? decision.labelName() : null));
    }

    /**
     * 计算撤销计划。三项校验全部在这里，调用方只负责按计划落库：
     * <ol>
     *   <li>必须存在最后一条交接动态（{@code ASSIGNEE_CHANGED}），撤销过的不能再撤销；</li>
     *   <li>该交接的接手人必须仍是当前负责人，否则说明中间有人手工改过，撤销会覆盖别人的编辑；</li>
     *   <li>迭代只在「还停在交接写入的那个值」时才回退，被改过就跳过（只退负责人）。</li>
     * </ol>
     */
    public RevokePlan planRevoke(Context ctx) {
        // 取最近一条交接/撤销动态：由 Mapper 的 ORDER BY ... LIMIT 1 完成，
        // 与 IssueTimelineMapper#latestHandoff（决定撤销按钮是否可见）共用同一取值规则，
        // 避免"按钮亮着、点下去却取到另一条"的割裂
        IssueActivity last = issueActivityMapper.selectLatestHandoffActivity(
                ctx.issueId(), ctx.category().name(), ACTION_ASSIGNEE_CHANGED, ACTION_HANDOFF_REVOKED);
        if (last == null || !ACTION_ASSIGNEE_CHANGED.equals(last.getAction())) {
            throw new ServiceException(ErrorCode.HANDOFF_NOTHING_TO_REVOKE);
        }
        UUID handedTo = ActivityValue.userId(last.getNewValue());
        UUID handedFrom = ActivityValue.userId(last.getOldValue());
        if (handedTo == null || !handedTo.equals(ctx.assigneeId())) {
            throw new ServiceException(ErrorCode.HANDOFF_ASSIGNEE_CHANGED);
        }

        // 迭代落点一起退回。标签不退：标签是可累加的，撤销这一刻它可能已经被别人手工加过，
        // 删掉等于覆盖别人的编辑（ProjectHandoff 上有同样口径的说明）。
        boolean sprintRestored = false;
        UUID restoredSprintId = null;
        String oldSprintId = ActivityValue.text(last.getOldValue(), "sprintId");
        String newSprintId = ActivityValue.text(last.getNewValue(), "sprintId");
        if (oldSprintId != null && newSprintId != null
                && Objects.equals(Uuids.parseOrNull(newSprintId), ctx.sprintId())) {
            // 旧值损坏（解析不出 id）时不写这一项，避免把迭代误清空
            restoredSprintId = oldSprintId.isBlank() ? null : Uuids.parseOrNull(oldSprintId);
            sprintRestored = restoredSprintId != null || oldSprintId.isBlank();
        }
        return new RevokePlan(handedFrom, handedTo, sprintRestored, restoredSprintId);
    }

    /**
     * 撤销后的副作用：写两条动态（撤销 + 可选的迭代回退），并提醒被退回的人。
     *
     * @param previousSprintId 撤销前的迭代（调用方在改实体之前传进来，用于动态的旧值）
     * @param actorId          执行撤销的人
     */
    public void recordRevoke(Context ctx, RevokePlan plan, UUID previousSprintId, UUID actorId) {
        recordActivity(ctx, ACTION_HANDOFF_REVOKED,
                ActivityValue.user(userNameOf(plan.toUserId()), plan.toUserId()),
                ActivityValue.user(userNameOf(plan.fromUserId()), plan.fromUserId()));
        if (plan.sprintRestored()) {
            // 时间线要能看到迭代也退回去了，否则用户只看到「负责人被退回」而迭代悄悄变了
            recordActivity(ctx, ACTION_SPRINT_CHANGED,
                    ActivityValue.field("sprint",
                            previousSprintId == null ? null : workflowService.sprintNameOf(previousSprintId)),
                    ActivityValue.field("sprint", plan.restoredSprintId() == null
                            ? null : workflowService.sprintNameOf(plan.restoredSprintId())));
        }
        if (plan.fromUserId() != null) {
            notificationService.notifyHandoffRevoked(plan.fromUserId(), ctx.projectId(), ctx.issueId(),
                    ctx.category().name(), ctx.issueNumber(), ctx.title(), userNameOf(actorId));
        }
    }

    /** 交接通知里的一句落点说明；两项都没配返回 null，通知正文与之前完全一致 */
    private String landingText(String sprintName, String labelName) {
        List<String> parts = new ArrayList<>(2);
        if (sprintName != null) {
            parts.add("已放进迭代「" + sprintName + "」");
        }
        if (labelName != null) {
            parts.add("已打上标签「" + labelName + "」");
        }
        return parts.isEmpty() ? null : String.join("，", parts);
    }

    /** 写入工作项动态：category 必须显式设置，否则需求/缺陷的动态查不到也删不掉 */
    private void recordActivity(Context ctx, String action, String oldValue, String newValue) {
        IssueActivity act = new IssueActivity();
        act.setId(UUID.randomUUID());
        act.setIssueId(ctx.issueId());
        act.setCategory(ctx.category().name());
        act.setActorId(UserContext.getUserId());
        act.setAction(action);
        act.setOldValue(oldValue);
        act.setNewValue(newValue);
        act.setCreatedAt(LocalDateTime.now());
        issueActivityMapper.insert(act);
    }

    /** 用户展示名：display_name 优先，兜底 username；用户不存在返回 null */
    private String userNameOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        String display = user.getDisplayName();
        return display == null || display.isBlank() ? user.getUsername() : display;
    }
}
