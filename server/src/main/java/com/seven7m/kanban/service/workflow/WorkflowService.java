package com.seven7m.kanban.service.workflow;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.workflow.HandoffTarget;
import com.seven7m.kanban.dto.workflow.SaveHandoffRequest;
import com.seven7m.kanban.dto.workflow.WorkflowColumnVO;
import com.seven7m.kanban.dto.workflow.WorkflowTransitionVO;
import com.seven7m.kanban.entity.handoff.ProjectHandoff;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.workflow.Workflow;

import java.util.List;
import java.util.UUID;

/**
 * 工作流规则查询：流转规则是看板拖拽的唯一事实来源。
 * 前端通过接口拉取规则做蓝/红提示，后端 move/update 时用同一套规则兜底校验。
 *
 * <p>工作流按工作项大类（TASK / REQUIREMENT / BUG）各有一份默认模板（V31 起）：
 * 任务「待办→进行中→已完成（/已取消）」、需求「待处理→设计中→开发中→测试中→已完成（/已取消）」、
 * 缺陷「待确认→再次打开→处理中→已修复→暂不修复→已关闭」。
 * 因此所有查询都必须带类别，不能再像此前那样取「项目唯一工作流」。
 *
 * <p>这是「工作流 / 状态节点 / 交接配置」的组合服务，不继承 {@code IService}：
 * 它同时读 workflow、workflow_status、workflow_transition、issue_status、project_handoff 等多张表，
 * 没有单一实体主表，硬套 {@code ServiceImpl} 只会制造一个假的实体归属。
 * 实现见 {@code com.seven7m.kanban.service.workflow.impl.WorkflowServiceImpl}。
 */
public interface WorkflowService {

    /** 某类别的默认工作流模板；未配置时返回 null */
    Workflow workflowOf(ItemCategory category);

    /** 某类别工作流的全部流转规则；项目不存在或未配置模板时返回空列表 */
    List<WorkflowTransitionVO> listTransitions(UUID projectId, ItemCategory category);

    /**
     * 某类别工作流的节点状态，按模板内的列顺序返回。
     * 看板列、详情状态下拉、新建默认状态都以此为准——issue_status 是全局字典，
     * 直接全量返回会把任务的状态混进缺陷看板。
     */
    List<IssueStatus> statusesOf(UUID projectId, ItemCategory category);

    /** 判断某类别工作项 from → to 是否允许流转；同状态（同列排序）恒允许 */
    boolean canTransition(UUID projectId, ItemCategory category, UUID fromStatusId, UUID toStatusId);

    /**
     * 项目某类别工作流的全部列 + 交接配置。
     * 看板列头与详情页状态下拉都从这里取，避免前端为每一列单独发请求。
     */
    List<WorkflowColumnVO> columnsOf(UUID projectId, ItemCategory category);

    /**
     * 项目某类别下的全部交接配置（一个状态至多一条）。
     *
     * <p>返回列表而不是 {@code Map<statusId, 配置>}：Map 作为方法返回值会让调用方
     * 靠猜键值含义，且「没有配置」这个常见情形还要额外约定是空 Map 还是 null；
     * 列表天然表达「可能为空」，需要按状态查时由调用方自行建索引（局部 Map 允许）。
     *
     * <p>看板装配时一次取完，禁止在列循环内查询。
     */
    List<ProjectHandoff> handoffConfigs(UUID projectId, ItemCategory category);

    /**
     * 进入 statusId 这一列后应交给谁。
     *
     * @param project 由调用方传入（update/move 本来就查过项目），避免为了解析再查一次
     * @return null 表示不交接：无配置、类型为 NONE、项目没有负责人、或目标成员已不存在
     */
    HandoffTarget resolveHandoffTarget(UUID projectId, ItemCategory category, UUID statusId, Project project);

    /** 单列交接配置；无配置返回 null */
    ProjectHandoff handoffConfigOf(UUID projectId, ItemCategory category, UUID statusId);

    /**
     * 保存（或清除）某一列的交接配置：handoffType = NONE 即清除，保留原负责人。
     * 只允许配置「当前类别工作流里真实存在的列」，否则会留下永远触发不了的死配置。
     */
    WorkflowColumnVO saveHandoff(UUID projectId, ItemCategory category, UUID statusId,
                                 SaveHandoffRequest req, UUID operatorId);

    /** 单个迭代名；写交接动态时用（调用方只持有 id，不想为此在自己那里再挂一个 mapper） */
    String sprintNameOf(UUID sprintId);
}
