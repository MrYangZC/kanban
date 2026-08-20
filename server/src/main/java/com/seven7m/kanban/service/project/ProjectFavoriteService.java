package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.project.ProjectFavorite;
import com.seven7m.kanban.mapper.project.ProjectFavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目收藏服务。
 * 收藏前置校验：用户为项目成员（非成员抛 403），未重复收藏（已收藏抛 400）。
 * 取消收藏校验：须存在收藏记录（未收藏抛 404）。
 */
@Service
@RequiredArgsConstructor
public class ProjectFavoriteService {

    private final ProjectFavoriteMapper projectFavoriteMapper;
    private final ProjectService projectService;

    @Transactional
    public void favorite(UUID projectId, UUID userId) {
        if (!projectService.isMember(projectId, userId)) {
            throw new ServiceException(403, "无权限");
        }
        if (isFavorited(projectId, userId)) {
            throw new ServiceException(400, "已收藏");
        }
        ProjectFavorite fav = new ProjectFavorite();
        fav.setId(UUID.randomUUID());
        fav.setProjectId(projectId);
        fav.setUserId(userId);
        fav.setCreatedAt(LocalDateTime.now());
        projectFavoriteMapper.insert(fav);
    }

    @Transactional
    public void unfavorite(UUID projectId, UUID userId) {
        if (!isFavorited(projectId, userId)) {
            throw new ServiceException(404, "未收藏该项目");
        }
        projectFavoriteMapper.delete(Wrappers.<ProjectFavorite>lambdaQuery()
                .eq(ProjectFavorite::getProjectId, projectId)
                .eq(ProjectFavorite::getUserId, userId));
    }

    public boolean isFavorited(UUID projectId, UUID userId) {
        return projectFavoriteMapper.selectCount(Wrappers.<ProjectFavorite>lambdaQuery()
                .eq(ProjectFavorite::getProjectId, projectId)
                .eq(ProjectFavorite::getUserId, userId)) > 0;
    }
}