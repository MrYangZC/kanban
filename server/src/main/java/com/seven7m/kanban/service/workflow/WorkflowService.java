package com.seven7m.kanban.service.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.enums.HandoffType;
import com.seven7m.kanban.common.enums.ItemCategory;
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
import java.util.UUID;

/**
 * 工作流规则查询：流转规则是看板拖拽的唯一事实来源。
 * 前端通过接口拉取规则做蓝/红提示，后端 move/update 时用同一套规则兜底校验。
 *
 * 工作流按工作项大类（TASK / REQUIREMENT / BUG）各有一份默认模板（V31 起）：
 * 任务「待办→进行中→已完成（/已取消）」、需求「待处理→设计中→开发中→测试中→已完成（/已取消）」、
 * 缺陷「待确认→再次打开→处理中→已修复→暂不修复→已关闭」。
 * 因此所有查询都必须带类别，不能再像此前那样取「项目唯一工作流」。
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

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

    /** 某类别的默认工作流模板；未配置时返回 null */
    public Workflow workflowOf(ItemCategory category) {
        ItemCategory cat = category == null ? ItemCategory.TASK : category;
        return workflowMapper.selectOne(Wrappers.<Workflow>lambdaQuery()
                .eq(Workflow::getAppliesTo, cat.name())
                .eq(Workflow::getIsDefault, true)
                .orderByAsc(Workflow::getId)
                .last("LIMIT 1"));
    }

    /** 某类别工作流的全部流转规则；项目不存在或未配置模板时返回空列表 */
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

    /**
     * 某类别工作流的节点状态，按模板内的列顺序返回。
     * 看板列、详情状态下拉、新建默认状态都以此为准——issue_status 是全局字典，
     * 直接全量返回会把任务的状态混进缺陷看板。
     */
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

    /** 判断某类别工作项 from → to 是否允许流转；同状态（同列排序）恒允许 */
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

    /**
     * 项目某类别工作流的全部列 + 交接配置。
     * 看板列头与详情页状态下拉都从这里取，避免前端为每一列单独发请求。
     */
    public List<WorkflowColumnVO> columnsOf(UUID projectId, ItemCategory category) {
        List<IssueStatus> statuses = statusesOf(projectId, category);
        if (statuses.isEmpty()) {
            return List.of();
        }
        Project project = projectMapper.selectById(projectId);
        Map<UUID, ProjectHandoff> configs = handoffMap(projectId, category);

        Set<UUID> userIds = new HashSet<>();
        Set<UUID> sprintIds = new HashSet<>();
        Set<UUID> labelIds = new HashSet<>();
        for (ProjectHandoff config : configs.values()) {
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
        Map<UUID, String> names = userNames(userIds);
        Map<UUID, String> sprintNames = sprintNames(sprintIds);
        Map<UUID, String> labelNames = labelNames(labelIds);

        return statuses.stream().map(st -> {
            ProjectHandoff config = configs.get(st.getId());
            HandoffType type = HandoffType.fromCode(config == null ? null : config.getHandoffType());
            UUID targetId = handoffResolver.resolve(config, project);
            // 解析不出人（项目没设负责人 / 指定成员已被删除）时展示为不交接：
            // 否则列头会写「交给 项目负责人」但实际不转交，显示与行为不一致
            if (targetId != null && !names.containsKey(targetId)) {
                targetId = null;
            }
            if (targetId == null) {
                type = HandoffType.NONE;
            }
            // 落点同理降级：指向的迭代/标签已经查不到就不显示，避免列头承诺一个不会发生的动作。
            // 迭代/标签被物理删除时外键 SET NULL 已经清空配置，这里兜的是「行还在但项目已不再引用」。
            UUID sprintId = config == null ? null : config.getTargetSprintId();
            if (sprintId != null && !sprintNames.containsKey(sprintId)) {
                sprintId = null;
            }
            UUID labelId = config == null ? null : config.getTargetLabelId();
            if (labelId != null && !labelNames.containsKey(labelId)) {
                labelId = null;
            }
            return new WorkflowColumnVO(
                    st.getId(), st.getName(), st.getCategory(), st.getColor(), st.getSortOrder(),
                    type.name(), targetId, targetId == null ? null : names.get(targetId),
                    config == null ? null : config.getHandoffNote(),
                    sprintId, sprintId == null ? null : sprintNames.get(sprintId),
                    labelId, labelId == null ? null : labelNames.get(labelId));
        }).toList();
    }

    /** 项目某类别下 statusId → 交接配置；看板装配时一次取完，禁止在列循环内查询 */
    public Map<UUID, ProjectHandoff> handoffMap(UUID projectId, ItemCategory category) {
        if (projectId == null) {
            return Map.of();
        }
        List<ProjectHandoff> list = projectHandoffMapper.selectList(
                Wrappers.<ProjectHandoff>lambdaQuery()
                        .eq(ProjectHandoff::getProjectId, projectId)
                        .eq(ProjectHandoff::getCategory, category.name()));
        Map<UUID, ProjectHandoff> map = new HashMap<>(list.size());
        for (ProjectHandoff config : list) {
            map.put(config.getStatusId(), config);
        }
        return map;
    }

    /**
     * 进入 statusId 这一列后应交给谁。
     *
     * @param project 由调用方传入（update/move 本来就查过项目），避免为了解析再查一次
     * @return null 表示不交接：无配置、类型为 NONE、项目没有负责人、或目标成员已不存在
     */
    public HandoffTarget resolveHandoffTarget(UUID projectId, ItemCategory category, UUID statusId, Project project) {
        if (projectId == null || statusId == null) {
            return null;
        }
        ProjectHandoff config = handoffConfigOf(projectId, category, statusId);
        UUID userId = handoffResolver.resolve(config, project);
        if (userId == null) {
            return null;
        }
        // 与 columnsOf 的降级口径保持一致：解析出的人若已不在系统里，按不交接处理
        String userName = userNames(Set.of(userId)).get(userId);
        if (userName == null) {
            return null;
        }
        // 落点查不到（迭代被删、标签被删）就只换人：交接本身比落点重要，不能因为落点残缺把人也不转了
        UUID sprintId = config.getTargetSprintId();
        String sprintName = sprintId == null ? null : sprintNames(Set.of(sprintId)).get(sprintId);
        if (sprintName == null) {
            sprintId = null;
        }
        UUID labelId = config.getTargetLabelId();
        String labelName = labelId == null ? null : labelNames(Set.of(labelId)).get(labelId);
        if (labelName == null) {
            labelId = null;
        }
        return new HandoffTarget(userId, userName, config.getHandoffNote(),
                sprintId, sprintName, labelId, labelName);
    }

    /** 单列交接配置；无配置返回 null */
    public ProjectHandoff handoffConfigOf(UUID projectId, ItemCategory category, UUID statusId) {
        return projectHandoffMapper.selectOne(Wrappers.<ProjectHandoff>lambdaQuery()
                .eq(ProjectHandoff::getProjectId, projectId)
                .eq(ProjectHandoff::getCategory, category.name())
                .eq(ProjectHandoff::getStatusId, statusId)
                .last("LIMIT 1"));
    }

    /**
     * 保存（或清除）某一列的交接配置：handoffType = NONE 即清除，保留原负责人。
     * 只允许配置「当前类别工作流里真实存在的列」，否则会留下永远触发不了的死配置。
     */
    @Transactional
    public WorkflowColumnVO saveHandoff(UUID projectId, ItemCategory category, UUID statusId,
                                        SaveHandoffRequest req, UUID operatorId) {
        if (projectId == null) {
            throw new ServiceException(400, "项目不存在");
        }
        if (statusId == null || req == null) {
            throw new ServiceException(400, "参数不能为空");
        }
        boolean inWorkflow = statusesOf(projectId, category).stream()
                .anyMatch(st -> st.getId().equals(statusId));
        if (!inWorkflow) {
            throw new ServiceException(400, "该状态不属于当前工作项类型的工作流");
        }

        HandoffType type = parseHandoffType(req.getHandoffType());
        String note = normalizeNote(req.getHandoffNote());
        UUID handoffUserId = req.getHandoffUserId();

        if (type == HandoffType.MEMBER) {
            if (handoffUserId == null) {
                throw new ServiceException(400, "请选择接手的成员");
            }
            if (sysUserMapper.selectById(handoffUserId) == null) {
                throw new ServiceException(400, "接手的成员不存在");
            }
            // 交给非项目成员会造出「看不见的任务」，这里提前拦住
            Long memberCount = projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                    .eq(ProjectMember::getProjectId, projectId)
                    .eq(ProjectMember::getUserId, handoffUserId));
            if (memberCount == null || memberCount == 0) {
                throw new ServiceException(400, "该用户不是项目成员");
            }
        }

        // 落点：NONE 一律清空，跟着类型走而不是单独留一个「只落点不换人」的半配置
        UUID targetSprintId = type == HandoffType.NONE ? null : req.getTargetSprintId();
        UUID targetLabelId = type == HandoffType.NONE ? null : req.getTargetLabelId();
        if (targetSprintId != null) {
            Sprint sprint = sprintMapper.selectById(targetSprintId);
            if (sprint == null || !projectId.equals(sprint.getProjectId())) {
                throw new ServiceException(400, "落点迭代不存在或不属于该项目");
            }
        }
        if (targetLabelId != null) {
            boolean inProject = issueTagMapper.selectByProject(projectId).stream()
                    .anyMatch(t -> t.getId().equals(targetLabelId));
            if (!inProject) {
                throw new ServiceException(400, "落点标签不在本项目的标签库里");
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
                .orElseThrow(() -> new ServiceException(404, "状态不存在"));
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
            return HandoffType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(400, "交接类型非法");
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
        if (note.length() > 500) {
            throw new ServiceException(400, "交接说明不能超过 500 字");
        }
        return note;
    }

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<UUID, String> map = new HashMap<>(users.size());
        for (SysUser user : users) {
            String display = user.getDisplayName();
            map.put(user.getId(), display == null || display.isBlank() ? user.getUsername() : display);
        }
        return map;
    }

    /** 单个迭代名；写交接动态时用（调用方只持有 id，不想为此在自己那里再挂一个 mapper） */
    public String sprintNameOf(UUID sprintId) {
        if (sprintId == null) {
            return null;
        }
        Sprint sprint = sprintMapper.selectById(sprintId);
        return sprint == null ? null : sprint.getName();
    }

    private Map<UUID, String> sprintNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Sprint> sprints = sprintMapper.selectBatchIds(ids);
        Map<UUID, String> map = new HashMap<>(sprints.size());
        for (Sprint sprint : sprints) {
            map.put(sprint.getId(), sprint.getName());
        }
        return map;
    }

    private Map<UUID, String> labelNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<IssueTag> labels = issueTagMapper.selectBatchIds(ids);
        Map<UUID, String> map = new HashMap<>(labels.size());
        for (IssueTag label : labels) {
            map.put(label.getId(), label.getName());
        }
        return map;
    }
}
