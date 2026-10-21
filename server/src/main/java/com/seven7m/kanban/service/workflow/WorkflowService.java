package com.seven7m.kanban.service.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.workflow.WorkflowTransitionVO;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.workflow.Workflow;
import com.seven7m.kanban.entity.workflow.WorkflowStatus;
import com.seven7m.kanban.entity.workflow.WorkflowTransition;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowStatusMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
}
