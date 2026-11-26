package com.seven7m.kanban.service.project.impl;

import com.seven7m.kanban.common.constant.PageLimits;
import com.seven7m.kanban.common.domain.NameIndex;
import com.seven7m.kanban.dto.project.ProjectActivityVO;
import com.seven7m.kanban.mapper.project.ProjectActivityMapper;
import com.seven7m.kanban.service.issue.comment.ActivityTextResolver;
import com.seven7m.kanban.service.project.ProjectActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 项目动态实现：批量取最近 N 条变更，再把状态 code 翻成中文名。
 *
 * <p>只读聚合（project_activity 不是单实体 CRUD），因此不继承 {@code ServiceImpl}；
 * 条数上下限复用 {@link PageLimits} 的"管理类列表缺省 20 / 上限 100"，
 * 与项目动态"时间线只展示最近若干条"的既有行为一致。
 */
@Service
@RequiredArgsConstructor
public class ProjectActivityServiceImpl implements ProjectActivityService {

    private final ProjectActivityMapper projectActivityMapper;
    private final ActivityTextResolver activityTextResolver;

    @Override
    public List<ProjectActivityVO> list(UUID projectId, int limit) {
        int size = limit <= 0 || limit > PageLimits.ADMIN_MAX_SIZE
                ? PageLimits.ADMIN_DEFAULT_SIZE
                : limit;
        List<ProjectActivityVO> rows = projectActivityMapper.selectByProject(projectId, size);
        // 与工作项动态一致：状态 code 翻成中文名，避免概览动态里出现 REQ_TODO 之类的英文
        NameIndex<String> statusNames = activityTextResolver.loadStatusNames();
        for (ProjectActivityVO vo : rows) {
            vo.setOldValue(activityTextResolver.toText(vo.getOldValue(), statusNames));
            vo.setNewValue(activityTextResolver.toText(vo.getNewValue(), statusNames));
        }
        return rows;
    }
}
