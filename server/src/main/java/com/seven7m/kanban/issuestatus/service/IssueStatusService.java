package com.seven7m.kanban.issuestatus.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.issuestatus.entity.IssueStatus;
import com.seven7m.kanban.issuestatus.mapper.IssueStatusMapper;
import com.seven7m.kanban.project.entity.Project;
import com.seven7m.kanban.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueStatusService {

    private final IssueStatusMapper issueStatusMapper;
    private final ProjectMapper projectMapper;

    /** 项目下全部状态（供详情页状态下拉与看板列定义），按 sortOrder 升序 */
    public List<IssueStatus> listByProject(UUID projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeleted() != null && project.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        return issueStatusMapper.selectList(Wrappers.<IssueStatus>lambdaQuery()
                .eq(IssueStatus::getProjectId, projectId)
                .orderByAsc(IssueStatus::getSortOrder));
    }
}
