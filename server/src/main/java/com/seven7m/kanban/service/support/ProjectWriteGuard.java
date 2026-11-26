package com.seven7m.kanban.service.support;

import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.project.ProjectStatus;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 归档项目的写保护。
 *
 * <p>归档此前只是把项目从"全部项目"列表里隐藏：成员仍可新建/编辑/拖动工作项、评论、传附件，
 * 甚至继续改成员，归档形同虚设。写路径统一走这里拦截；项目自身的"取消归档/删除"由
 * ProjectService 处理，不经过本守卫。
 */
@Component
@RequiredArgsConstructor
public class ProjectWriteGuard {

    private final ProjectMapper projectMapper;

    /** 项目存在且未归档才放行；项目不存在时交由调用方按 404 处理 */
    public void requireWritable(UUID projectId) {
        if (projectId == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project != null && ProjectStatus.ARCHIVED == ProjectStatus.of(project.getStatus())) {
            throw new ServiceException(ErrorCode.PROJECT_ARCHIVED_READONLY);
        }
    }
}
