package com.seven7m.kanban.service.project.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.project.ProjectFavorite;
import com.seven7m.kanban.mapper.project.ProjectFavoriteMapper;
import com.seven7m.kanban.service.project.ProjectFavoriteService;
import com.seven7m.kanban.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目收藏实现。
 *
 * <p>单实体 CRUD 型，继承 {@link ServiceImpl} 后可省掉 insert/delete/selectCount 三类样板，
 * 只保留领域校验（成员身份、重复收藏）。
 */
@Service
@RequiredArgsConstructor
public class ProjectFavoriteServiceImpl extends ServiceImpl<ProjectFavoriteMapper, ProjectFavorite>
        implements ProjectFavoriteService {

    private final ProjectService projectService;

    @Override
    @Transactional
    public void favorite(UUID projectId, UUID userId) {
        if (!projectService.isMember(projectId, userId)) {
            throw new ServiceException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        if (isFavorited(projectId, userId)) {
            throw new ServiceException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        ProjectFavorite fav = new ProjectFavorite();
        fav.setId(UUID.randomUUID());
        fav.setProjectId(projectId);
        fav.setUserId(userId);
        fav.setCreatedAt(LocalDateTime.now());
        save(fav);
    }

    @Override
    @Transactional
    public void unfavorite(UUID projectId, UUID userId) {
        if (!isFavorited(projectId, userId)) {
            throw new ServiceException(ErrorCode.FAVORITE_NOT_FOUND);
        }
        remove(Wrappers.<ProjectFavorite>lambdaQuery()
                .eq(ProjectFavorite::getProjectId, projectId)
                .eq(ProjectFavorite::getUserId, userId));
    }

    @Override
    public boolean isFavorited(UUID projectId, UUID userId) {
        return count(Wrappers.<ProjectFavorite>lambdaQuery()
                .eq(ProjectFavorite::getProjectId, projectId)
                .eq(ProjectFavorite::getUserId, userId)) > 0;
    }
}
