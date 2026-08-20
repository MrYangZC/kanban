package com.seven7m.kanban.service.project;

import com.seven7m.kanban.dto.project.ProjectActivityVO;
import com.seven7m.kanban.mapper.project.ProjectActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    private final ProjectActivityMapper projectActivityMapper;

    public List<ProjectActivityVO> list(UUID projectId, int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
        return projectActivityMapper.selectByProject(projectId, limit);
    }
}