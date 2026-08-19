package com.seven7m.kanban.project.service;

import com.seven7m.kanban.project.dto.ProjectActivityVO;
import com.seven7m.kanban.project.mapper.ProjectActivityMapper;
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