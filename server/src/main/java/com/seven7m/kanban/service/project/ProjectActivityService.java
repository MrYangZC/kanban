package com.seven7m.kanban.service.project;

import com.seven7m.kanban.dto.project.ProjectActivityVO;
import com.seven7m.kanban.mapper.project.ProjectActivityMapper;
import com.seven7m.kanban.service.issue.comment.ActivityTextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    private final ProjectActivityMapper projectActivityMapper;
    private final ActivityTextResolver activityTextResolver;

    public List<ProjectActivityVO> list(UUID projectId, int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
        List<ProjectActivityVO> rows = projectActivityMapper.selectByProject(projectId, limit);
        // 与工作项动态一致：状态 code 翻成中文名，避免概览动态里出现 REQ_TODO 之类的英文
        Map<String, String> statusNames = activityTextResolver.loadStatusNames();
        for (ProjectActivityVO vo : rows) {
            vo.setOldValue(activityTextResolver.toText(vo.getOldValue(), statusNames));
            vo.setNewValue(activityTextResolver.toText(vo.getNewValue(), statusNames));
        }
        return rows;
    }
}