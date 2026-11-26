package com.seven7m.kanban.service.workflow.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.domain.NameIndex;
import com.seven7m.kanban.common.enums.HandoffType;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.workflow.HandoffTarget;
import com.seven7m.kanban.dto.workflow.SaveHandoffRequest;
import com.seven7m.kanban.dto.workflow.WorkflowColumnVO;
import com.seven7m.kanban.dto.workflow.WorkflowTransitionVO;
import com.seven7m.kanban.entity.handoff.ProjectHandoff;
import com.seven7m.kanban.entity.issue.tag.IssueTag;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.project.ProjectMember;
import com.seven7m.kanban.entity.sprint.Sprint;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.entity.workflow.Workflow;
import com.seven7m.kanban.entity.workflow.WorkflowStatus;
import com.seven7m.kanban.entity.workflow.WorkflowTransition;
import com.seven7m.kanban.mapper.handoff.ProjectHandoffMapper;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.project.ProjectMemberMapper;
import com.seven7m.kanban.mapper.sprint.SprintMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowStatusMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowTransitionMapper;
import com.seven7m.kanban.service.workflow.HandoffResolver;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

/**
 * 工作流规则查询实现：流转规则是看板拖拽的唯一事实来源。
 * 前端通过接口拉取规则做蓝/红提示，后端 move/update 时用同一套规则兜底校验。
 *
 * <p>工作流按工作项大类（TASK / REQUIREMENT / BUG）各有一份默认模板（V31 起）：
 * 任务「待办→进行中→已完成（/已取消）」、需求「待处理→设计中→开发中→测试中→已完成（/已取消）」、
 * 缺陷「待确认→再次打开→处理中→已修复→暂不修复→已关闭」。
 * 因此所有查询都必须带类别，不能再像此前那样取「项目唯一工作流」。
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    /** 交接说明的最大字数（与 ErrorCode.HANDOFF_NOTE_TOO_LONG 的文案口径一致） */
    private static final int HANDOFF_NOTE_MAX_LENGTH = 500;

    private final WorkflowMapper workflowMapper;
    private final WorkflowStatusMapper workflowStatusMapper;
    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final ProjectMapper projectMapper;
    private final ProjectHandoffMapper projectHandoffMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final SysUserMapper sysUserMapper;
    private final SprintMapper sprintMapper;
    private final IssueTagMapper issueTagMapper;
    private final HandoffResolver handoffResolver;

    @Override
    public Workflow workflowOf(ItemCategory category) {
        ItemCategory cat = category == null ? ItemCategory.TASK : category;
        // 历史上这里用 selectOne(...).last("LIMIT 1")；在 Wrapper 上拼 SQL 属于越层写 SQL，
        // 且「只取第一条」完全可以用取列表 + findFirst 表达，语义不变
        return workflowMapper.selectList(Wrappers.<Workflow>lambdaQuery()
                        .eq(Workflow::getAppliesTo, cat.name())
                        .eq(Workflow::getDefaultFlag, true)
                        .orderByAsc(Workflow::getId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<WorkflowTransitionVO> listTransitions(UUID projectId, ItemCategory category) {
        Workflow workflow = projectWorkflow(projectId, category);
        if (workflow == null) {
            return List.of();
        }
        return workflowTransitionMapper.selectList(
                        Wrappers.<WorkflowTransition>lambdaQuery()
                                .eq(WorkflowTransition::getWorkflowId, workflow.getId()))
                .stream()
                .map(t -> new WorkflowTransitionVO(t.getFromStatusId(), t.getToStatusId(), t.getName()))
                .toList();
    }

    @Override
    public List<IssueStatus> statusesOf(UUID projectId, ItemCategory category) {
        Workflow workflow = projectWorkflow(projectId, category);
        if (workflow == null) {
            return List.of();
        }
        List<WorkflowStatus> nodes = workflowStatusMapper.selectList(
                Wrappers.<WorkflowStatus>lambdaQuery()
                        .eq(WorkflowStatus::getWorkflowId, workflow.getId())
                        .orderByAsc(WorkflowStatus::getSortOrder));
        if (nodes.isEmpty()) {
            return List.of();
        }
        List<UUID> statusIds = nodes.stream().map(WorkflowStatus::getStatusId).toList();
        Map<UUID, IssueStatus> byId = new LinkedHashMap<>();
        for (IssueStatus st : issueStatusMapper.selectBatchIds(statusIds)) {
            byId.put(st.getId(), st);
        }
        List<IssueStatus> ordered = new ArrayList<>(statusIds.size());
        for (UUID id : statusIds) {
            IssueStatus st = byId.get(id);
            if (st != null) {
                ordered.add(st);
            }
        }
        return ordered;
    }

    @Override
    public boolean canTransition(UUID projectId, ItemCategory category, UUID fromStatusId, UUID toStatusId) {
        if (fromStatusId == null || toStatusId == null || fromStatusId.equals(toStatusId)) {
            return true;
        }
        Workflow workflow = projectWorkflow(projectId, category);
        if (workflow == null) {
            return false;
        }
        Long count = workflowTransitionMapper.selectCount(
                Wrappers.<WorkflowTransition>lambdaQuery()
                        .eq(WorkflowTransition::getWorkflowId, workflow.getId())
                        .eq(WorkflowTransition::getFromStatusId, fromStatusId)
                        .eq(WorkflowTransition::getToStatusId, toStatusId));
        return count != null && count > 0;
    }

    /** 项目存在且该类工作流模板可用时返回模板；否则 null */
    private Workflow projectWorkflow(UUID projectId, ItemCategory category) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return null;
        }
        return workflowOf(category);
    }

    // ========================= 交接（handoff） =========================

    @Override
    public List<WorkflowColumnVO> columnsOf(UUID projectId, ItemCategory category) {
        List<IssueStatus> statuses = statusesOf(projectId, category);
        if (statuses.isEmpty()) {
            return List.of();
        }
        Project project = projectMapper.selectById(projectId);
        List<ProjectHandoff> configs = handoffConfigs(projectId, category);
        // 局部索引允许用 Map 累积：一次遍历后按 statusId 直接取，避免列循环里再查
        Map<UUID, ProjectHandoff> configByStatus = new HashMap<>(configs.size());
        for (ProjectHandoff config : configs) {
            configByStatus.put(config.getStatusId(), config);
        }

        Set<UUID> userIds = new HashSet<>();
        Set<UUID> sprintIds = new HashSet<>();
        Set<UUID> labelIds = new HashSet<>();
        for (ProjectHandoff config : configs) {
            if (config.getHandoffUserId() != null) {
                userIds.add(config.getHandoffUserId());
            }
            if (config.getTargetSprintId() != null) {
                sprintIds.add(config.getTargetSprintId());
            }
            if (config.getTargetLabelId() != null) {
                labelIds.add(config.getTargetLabelId());
            }
        }
        if (project != null && project.getLeadUserId() != null) {
            userIds.add(project.getLeadUserId());
        }
        NameIndex<UUID> names = userNames(userIds);
        NameIndex<UUID> sprintNameIndex = sprintNames(sprintIds);
        NameIndex<UUID> labelNameIndex = labelNames(labelIds);

        return statuses.stream().map(st -> {
            ProjectHandoff config = configByStatus.get(st.getId());
            HandoffType type = HandoffType.fromCode(config == null ? null : config.getHandoffType());
            UUID targetId = handoffResolver.resolve(config, project);
            // 解析不出人（项目没设负责人 / 指定成员已被删除）时展示为不交接：
            // 否则列头会写「交给 项目负责人」但实际不转交，显示与行为不一致
            if (targetId != null && !names.contains(targetId)) {
                targetId = null;
            }
            if (targetId == null) {
                type = HandoffType.NONE;
            }
            // 落点同理降级：指向的迭代/标签已经查不到就不显示，避免列头承诺一个不会发生的动作。
            // 迭代/标签被物理删除时外键 SET NULL 已经清空配置，这里兜的是「行还在但项目已不再引用」。
            UUID sprintId = config == null ? null : config.getTargetSprintId();
            if (sprintId != null && !sprintNameIndex.contains(sprintId)) {
                sprintId = null;
            }
            UUID labelId = config == null ? null : config.getTargetLabelId();
            if (labelId != null && !labelNameIndex.contains(labelId)) {
                labelId = null;
            }
            return new WorkflowColumnVO(
                    st.getId(), st.getName(), st.getCategory(), st.getColor(), st.getSortOrder(),
                    type.name(), targetId, targetId == null ? null : names.nameOf(targetId),
                    config == null ? null : config.getHandoffNote(),
                    sprintId, sprintId == null ? null : sprintNameIndex.nameOf(sprintId),
                    labelId, labelId == null ? null : labelNameIndex.nameOf(labelId));
        }).toList();
    }

    @Override
    public List<ProjectHandoff> handoffConfigs(UUID projectId, ItemCategory category) {
        if (projectId == null) {
            return List.of();
        }
        return projectHandoffMapper.selectList(
                Wrappers.<ProjectHandoff>lambdaQuery()
                        .eq(ProjectHandoff::getProjectId, projectId)
                        .eq(ProjectHandoff::getCategory, category.name()));
    }

    @Override
    public HandoffTarget resolveHandoffTarget(UUID projectId, ItemCategory category, UUID statusId,
                                              Project project) {
        if (projectId == null || statusId == null) {
            return null;
        }
        ProjectHandoff config = handoffConfigOf(projectId, category, statusId);
        UUID userId = handoffResolver.resolve(config, project);
        if (userId == null) {
            return null;
        }
        // 与 columnsOf 的降级口径保持一致：解析出的人若已不在系统里，按不交接处理。
        // 单行场景直接按主键查：此前用 Set.of(id) + selectBatchIds，只查 1 行却走 IN 语法
        String userName = userNameOf(userId);
        if (userName == null) {
            return null;
        }
        // 落点查不到（迭代被删、标签被删）就只换人：交接本身比落点重要，不能因为落点残缺把人也不转了
        UUID sprintId = config.getTargetSprintId();
        String sprintName = sprintId == null ? null : sprintNameOf(sprintId);
        if (sprintName == null) {
            sprintId = null;
        }
        UUID labelId = config.getTargetLabelId();
        String labelName = labelId == null ? null : labelNameOf(labelId);
        if (labelName == null) {
            labelId = null;
        }
        return new HandoffTarget(userId, userName, config.getHandoffNote(),
                sprintId, sprintName, labelId, labelName);
    }

    @Override
    public ProjectHandoff handoffConfigOf(UUID projectId, ItemCategory category, UUID statusId) {
        // 同上：不用 last("LIMIT 1")，取列表后取第一条
        return projectHandoffMapper.selectList(Wrappers.<ProjectHandoff>lambdaQuery()
                        .eq(ProjectHandoff::getProjectId, projectId)
                        .eq(ProjectHandoff::getCategory, category.name())
                        .eq(ProjectHandoff::getStatusId, statusId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public WorkflowColumnVO saveHandoff(UUID projectId, ItemCategory category, UUID statusId,
                                        SaveHandoffRequest req, UUID operatorId) {
        if (projectId == null) {
            throw new ServiceException(ErrorCode.PROJECT_MISSING);
        }
        if (statusId == null || req == null) {
            throw new ServiceException(ErrorCode.PARAM_REQUIRED);
        }
        boolean inWorkflow = statusesOf(projectId, category).stream()
                .anyMatch(st -> st.getId().equals(statusId));
        if (!inWorkflow) {
            throw new ServiceException(ErrorCode.STATUS_NOT_IN_WORKFLOW);
        }

        HandoffType type = parseHandoffType(req.getHandoffType());
        String note = normalizeNote(req.getHandoffNote());
        UUID handoffUserId = req.getHandoffUserId();

        if (type == HandoffType.MEMBER) {
            if (handoffUserId == null) {
                throw new ServiceException(ErrorCode.HANDOFF_MEMBER_REQUIRED);
            }
            if (sysUserMapper.selectById(handoffUserId) == null) {
                throw new ServiceException(ErrorCode.HANDOFF_MEMBER_NOT_FOUND);
            }
            // 交给非项目成员会造出「看不见的任务」，这里提前拦住
            Long memberCount = projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                    .eq(ProjectMember::getProjectId, projectId)
                    .eq(ProjectMember::getUserId, handoffUserId));
            if (memberCount == null || memberCount == 0) {
                throw new ServiceException(ErrorCode.HANDOFF_MEMBER_NOT_IN_PROJECT);
            }
        }

        // 落点：NONE 一律清空，跟着类型走而不是单独留一个「只落点不换人」的半配置
        UUID targetSprintId = type == HandoffType.NONE ? null : req.getTargetSprintId();
        UUID targetLabelId = type == HandoffType.NONE ? null : req.getTargetLabelId();
        if (targetSprintId != null) {
            Sprint sprint = sprintMapper.selectById(targetSprintId);
            if (sprint == null || !projectId.equals(sprint.getProjectId())) {
                throw new ServiceException(ErrorCode.HANDOFF_SPRINT_IN_OTHER_PROJECT);
            }
        }
        if (targetLabelId != null) {
            boolean inProject = issueTagMapper.selectByProject(projectId).stream()
                    .anyMatch(t -> t.getId().equals(targetLabelId));
            if (!inProject) {
                throw new ServiceException(ErrorCode.HANDOFF_LABEL_NOT_IN_LIBRARY);
            }
        }

        ProjectHandoff existing = handoffConfigOf(projectId, category, statusId);
        if (existing == null) {
            if (type == HandoffType.NONE) {
                // 本来就没有配置，无需落一条空记录
                return columnOf(projectId, category, statusId);
            }
            ProjectHandoff created = new ProjectHandoff();
            created.setId(UUID.randomUUID());
            created.setProjectId(projectId);
            created.setCategory(category.name());
            created.setStatusId(statusId);
            applyHandoffFields(created, type, handoffUserId, note, targetSprintId, targetLabelId);
            created.setUpdatedBy(operatorId);
            created.setUpdatedAt(LocalDateTime.now());
            projectHandoffMapper.insert(created);
        } else {
            applyHandoffFields(existing, type, handoffUserId, note, targetSprintId, targetLabelId);
            existing.setUpdatedBy(operatorId);
            existing.setUpdatedAt(LocalDateTime.now());
            projectHandoffMapper.updateById(existing);
        }
        return columnOf(projectId, category, statusId);
    }

    private WorkflowColumnVO columnOf(UUID projectId, ItemCategory category, UUID statusId) {
        return columnsOf(projectId, category).stream()
                .filter(c -> c.statusId().equals(statusId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.STATUS_NOT_FOUND));
    }

    private void applyHandoffFields(ProjectHandoff target, HandoffType type, UUID handoffUserId, String note,
                                    UUID targetSprintId, UUID targetLabelId) {
        target.setHandoffType(type.name());
        // 只有 MEMBER 才留人，其余类型必须置空，否则 CHECK 约束与展示都会矛盾
        target.setHandoffUserId(type == HandoffType.MEMBER ? handoffUserId : null);
        target.setHandoffNote(note);
        target.setTargetSprintId(targetSprintId);
        target.setTargetLabelId(targetLabelId);
    }

    /** 注意不能用 HandoffType.fromCode：它会把非法值静默降级，保存配置时应当直接报错 */
    private HandoffType parseHandoffType(String raw) {
        if (raw == null || raw.isBlank()) {
            return HandoffType.NONE;
        }
        try {
            return HandoffType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ErrorCode.HANDOFF_TYPE_INVALID);
        }
    }

    private String normalizeNote(String raw) {
        if (raw == null) {
            return null;
        }
        String note = raw.trim();
        if (note.isEmpty()) {
            return null;
        }
        if (note.length() > HANDOFF_NOTE_MAX_LENGTH) {
            throw new ServiceException(ErrorCode.HANDOFF_NOTE_TOO_LONG);
        }
        return note;
    }

    /** 批量取用户显示名：displayName 优先，缺失时回落 username；返回只读名称索引 */
    private NameIndex<UUID> userNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return NameIndex.empty();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<UUID, String> names = new HashMap<>(users.size());
        for (SysUser user : users) {
            String display = user.getDisplayName();
            names.put(user.getId(), display == null || display.isBlank() ? user.getUsername() : display);
        }
        return NameIndex.of(names);
    }

    /** 单取用户显示名（主键查询）：displayName 优先，缺失时回落 username；查不到返回 null */
    private String userNameOf(UUID userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        String display = user.getDisplayName();
        return display == null || display.isBlank() ? user.getUsername() : display;
    }

    @Override
    public String sprintNameOf(UUID sprintId) {
        if (sprintId == null) {
            return null;
        }
        Sprint sprint = sprintMapper.selectById(sprintId);
        return sprint == null ? null : sprint.getName();
    }

    /** 批量取迭代名；返回只读名称索引 */
    private NameIndex<UUID> sprintNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return NameIndex.empty();
        }
        List<Sprint> sprints = sprintMapper.selectBatchIds(ids);
        Map<UUID, String> names = new HashMap<>(sprints.size());
        for (Sprint sprint : sprints) {
            names.put(sprint.getId(), sprint.getName());
        }
        return NameIndex.of(names);
    }

    /** 批量取标签名；返回只读名称索引 */
    private NameIndex<UUID> labelNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return NameIndex.empty();
        }
        List<IssueTag> labels = issueTagMapper.selectBatchIds(ids);
        Map<UUID, String> names = new HashMap<>(labels.size());
        for (IssueTag label : labels) {
            names.put(label.getId(), label.getName());
        }
        return NameIndex.of(names);
    }

    /** 单取标签名（主键查询）；查不到返回 null，调用方据此把落点降级为空 */
    private String labelNameOf(UUID labelId) {
        IssueTag label = issueTagMapper.selectById(labelId);
        return label == null ? null : label.getName();
    }
}
