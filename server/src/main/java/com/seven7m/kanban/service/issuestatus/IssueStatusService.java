package com.seven7m.kanban.service.issuestatus;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;

import java.util.List;
import java.util.UUID;

/**
 * 项目状态节点查询接口。
 *
 * <p>这里**不**继承 {@code IService}：本服务不直接管理 {@code issue_status} 实体
 * （状态字典的归属在 {@code WorkflowService}），只校验项目是否存在后转发查询；
 * 为了凑通用 CRUD 而注入一个用不到的 {@code IssueStatusMapper} 只会多一条废依赖。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.issuestatus.impl.IssueStatusServiceImpl}。
 */
public interface IssueStatusService {

    /**
     * 某类别工作流的节点状态（全局字典 issue_status 的类别子集），按模板列顺序。
     *
     * @param projectId 项目 id；不存在抛 404
     * @param category  工作项类别（TASK/REQUIREMENT/BUG），三类各有自己的节点
     */
    List<IssueStatus> listByProject(UUID projectId, ItemCategory category);
}
