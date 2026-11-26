package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.entity.project.ProjectFavorite;

import java.util.UUID;

/**
 * 项目收藏服务。
 * 收藏前置校验：用户为项目成员（非成员抛 403），未重复收藏（已收藏抛 400）。
 * 取消收藏校验：须存在收藏记录（未收藏抛 404）。
 *
 * <p>继承 {@link IService} 复用 MyBatis-Plus 的通用能力（保存、条件删除、计数），
 * 三个领域方法仍在此声明。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.project.impl.ProjectFavoriteServiceImpl}。
 */
public interface ProjectFavoriteService extends IService<ProjectFavorite> {

    /** 收藏项目：须为项目成员且尚未收藏 */
    void favorite(UUID projectId, UUID userId);

    /** 取消收藏：须存在收藏记录 */
    void unfavorite(UUID projectId, UUID userId);

    /** 是否已收藏该项目 */
    boolean isFavorited(UUID projectId, UUID userId);
}
