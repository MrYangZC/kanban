package com.seven7m.kanban.service.issue.reference;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.issue.tag.ProjectLabel;
import com.seven7m.kanban.entity.sprint.Sprint;
import com.seven7m.kanban.mapper.issue.tag.ProjectLabelMapper;
import com.seven7m.kanban.mapper.sprint.SprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * 工作项「外部引用」的项目归属校验。
 *
 * <p>此前只靠数据库外键保证“对象存在”，于是可以把工作项挂到**别的项目**的迭代上、
 * 或打上不在本项目标签库里的标签：界面上的下拉选不到这些对象，数据看起来“正常落库”，
 * 实际已经跨项目串了引用（统计、筛选、交接落点都会跟着错）。
 *
 * <p>统一在这里校验，避免每个 Service 各写一套。
 */
@Component
@RequiredArgsConstructor
public class IssueReferenceGuard {

    private final SprintMapper sprintMapper;
    private final ProjectLabelMapper projectLabelMapper;

    /** 迭代必须存在且属于该项目 */
    public void requireSprintInProject(UUID projectId, UUID sprintId) {
        if (projectId == null || sprintId == null) {
            return;
        }
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null || !projectId.equals(sprint.getProjectId())) {
            throw new ServiceException(400, "迭代不存在或不属于该项目");
        }
    }

    /** 标签必须全部在该项目的标签库中（project_label） */
    public void requireLabelsInProject(UUID projectId, Collection<UUID> labelIds) {
        if (projectId == null || labelIds == null || labelIds.isEmpty()) {
            return;
        }
        long distinct = labelIds.stream().filter(Objects::nonNull).distinct().count();
        if (distinct == 0) {
            return;
        }
        Long exists = projectLabelMapper.selectCount(Wrappers.<ProjectLabel>lambdaQuery()
                .eq(ProjectLabel::getProjectId, projectId)
                .in(ProjectLabel::getLabelId, labelIds));
        if (exists == null || exists < distinct) {
            throw new ServiceException(400, "标签不在本项目的标签库中");
        }
    }
}
