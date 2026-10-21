package com.seven7m.kanban.service.issuestatus;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueStatusService {

    private final WorkflowService workflowService;
    private final ProjectMapper projectMapper;

    /**
     * 某类别工作流的节点状态（全局字典 issue_status 的类别子集），按模板列顺序。
     * issue_status 是全局字典，三类工作项各有自己的节点，不能直接全量返回。
     */
    public List<IssueStatus> listByProject(UUID projectId, ItemCategory category) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException(404, "项目不存在");
        }
        return workflowService.statusesOf(projectId, category);
    }
}
