package com.seven7m.kanban.service.issuestatus;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueStatusService {

    private final IssueStatusMapper issueStatusMapper;
    private final ProjectMapper projectMapper;

    /** 全局状态字典（全局化后状态不再按项目隔离），按 sortOrder 升序 */
    public List<IssueStatus> listByProject(UUID projectId) {
        // 仍校验项目存在（接口以项目为入口），但状态本身为全局字典
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeleted() != null && project.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        return issueStatusMapper.selectList(Wrappers.<IssueStatus>lambdaQuery()
                .orderByAsc(IssueStatus::getSortOrder));
    }
}
