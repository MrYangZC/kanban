package com.seven7m.kanban.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.workflow.dto.WorkflowTransitionVO;
import com.seven7m.kanban.workflow.entity.Workflow;
import com.seven7m.kanban.workflow.entity.WorkflowTransition;
import com.seven7m.kanban.workflow.mapper.WorkflowMapper;
import com.seven7m.kanban.workflow.mapper.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 工作流规则查询：项目默认工作流的流转规则是看板拖拽的唯一事实来源。
 * 前端通过接口拉取规则做蓝/红提示，后端 move 时用同一套规则兜底校验。
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowTransitionMapper workflowTransitionMapper;

    /** 项目默认工作流的全部流转规则；未配置默认工作流时返回空列表 */
    public List<WorkflowTransitionVO> listTransitions(UUID projectId) {
        Workflow workflow = defaultWorkflow(projectId);
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

    /** 判断 from → to 是否允许流转；同状态（同列排序）恒允许 */
    public boolean canTransition(UUID projectId, UUID fromStatusId, UUID toStatusId) {
        if (fromStatusId == null || toStatusId == null || fromStatusId.equals(toStatusId)) {
            return true;
        }
        Workflow workflow = defaultWorkflow(projectId);
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

    private Workflow defaultWorkflow(UUID projectId) {
        return workflowMapper.selectOne(
                Wrappers.<Workflow>lambdaQuery()
                        .eq(Workflow::getProjectId, projectId)
                        .eq(Workflow::getIsDefault, true)
                        .last("LIMIT 1"));
    }
}
