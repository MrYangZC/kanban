package com.seven7m.kanban.service.project;

import com.seven7m.kanban.dto.project.ProjectActivityVO;

import java.util.List;
import java.util.UUID;

/**
 * 项目动态（project_activity 聚合）的只读服务：最近 N 条工作项变更，用于项目首页时间线。
 *
 * <p>只有读取能力、且是跨表聚合，因此不套 MyBatis-Plus 的 {@code IService} 惯例
 * （没有单实体 CRUD 可复用），保持「接口 + 实现」两层。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.project.impl.ProjectActivityServiceImpl}。
 */
public interface ProjectActivityService {

    /**
     * 查询项目最近的工作项动态。
     *
     * @param projectId 项目 id
     * @param limit     条数，越界（≤0 或超过上限）时回落为缺省值，避免一次拉全量历史
     */
    List<ProjectActivityVO> list(UUID projectId, int limit);
}
