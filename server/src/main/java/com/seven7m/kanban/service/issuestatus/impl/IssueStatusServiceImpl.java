package com.seven7m.kanban.service.issuestatus.impl;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.service.issuestatus.IssueStatusService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 项目状态节点查询实现：只做"项目存在性校验 + 转发到工作流"，不动 issue_status 表本身。
 */
@Service
@RequiredArgsConstructor
public class IssueStatusServiceImpl implements IssueStatusService {

    private final WorkflowService workflowService;
    private final ProjectMapper projectMapper;

    /**
     * 某类别工作流的节点状态（全局字典 issue_status 的类别子集），按模板列顺序。
     * issue_status 是全局字典，三类工作项各有自己的节点，不能直接全量返回。
     */
    @Override
    public List<IssueStatus> listByProject(UUID projectId, ItemCategory category) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return workflowService.statusesOf(projectId, category);
    }
}
