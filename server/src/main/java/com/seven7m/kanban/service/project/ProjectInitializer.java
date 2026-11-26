package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.board.Board;
import com.seven7m.kanban.entity.board.BoardColumn;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.workflow.Workflow;
import com.seven7m.kanban.entity.workflow.WorkflowStatus;
import com.seven7m.kanban.mapper.board.BoardColumnMapper;
import com.seven7m.kanban.mapper.board.BoardMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 项目创建即初始化（文档第28节）。
 * 全局化重构后，issue_type / issue_status / workflow 均为全局主数据（由 Flyway 迁移灌入），
 * 项目创建时不再逐项目复制，仅：
 *   1. 绑定 project.workflow_id 指向默认工作流模板；
 *   2. 基于默认工作流的列顺序创建默认 Board + board_column（列仅映射 status_id，不冗余 name）。
 *
 * 注：本类是执行业务写操作的服务组件，此前放在 entity 包下属于分层错位，已移至 service 包。
 */
@Component
@RequiredArgsConstructor
public class ProjectInitializer {

    /** 新项目自动创建的看板名称与类型：与前端看板列表的展示口径一致 */
    private static final String DEFAULT_BOARD_NAME = "默认看板";
    private static final String BOARD_TYPE_KANBAN = "KANBAN";

    private final WorkflowMapper workflowMapper;
    private final WorkflowStatusMapper workflowStatusMapper;
    private final BoardMapper boardMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final ProjectMapper projectMapper;

    @Transactional
    public void initDefaults(UUID projectId, UUID operator) {
        // V31 起每类工作项各有一条默认模板（TASK/REQUIREMENT/BUG）；
        // project.workflow_id 仍指向缺陷流（默认看板列按它建），其它类别由 WorkflowService 按类别解析
        Workflow defaultWf = workflowMapper.selectList(Wrappers.<Workflow>lambdaQuery()
                        .eq(Workflow::getDefaultFlag, true)
                        .eq(Workflow::getAppliesTo, ItemCategory.BUG.name())
                        .orderByAsc(Workflow::getId))
                .stream().findFirst().orElse(null);

        // 1. 绑定项目 -> 默认工作流模板
        projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
                .eq(Project::getId, projectId)
                .set(Project::getWorkflowId, defaultWf == null ? null : defaultWf.getId()));

        // 2. 默认工作流的列顺序（workflow_status -> status_id）
        List<UUID> statusIds = new ArrayList<>();
        if (defaultWf != null) {
            List<WorkflowStatus> wfStatuses = workflowStatusMapper.selectList(
                    Wrappers.<WorkflowStatus>lambdaQuery()
                            .eq(WorkflowStatus::getWorkflowId, defaultWf.getId())
                            .orderByAsc(WorkflowStatus::getSortOrder));
            for (WorkflowStatus ws : wfStatuses) {
                statusIds.add(ws.getStatusId());
            }
        }

        // 3. 默认看板 + 列（列仅映射 status_id，name 由 status 联查获取）
        Board board = new Board();
        board.setId(UUID.randomUUID());
        board.setProjectId(projectId);
        board.setName(DEFAULT_BOARD_NAME);
        board.setBoardType(BOARD_TYPE_KANBAN);
        board.setCreatedBy(operator);
        board.setCreatedAt(LocalDateTime.now());
        board.setUpdatedAt(LocalDateTime.now());
        boardMapper.insert(board);

        for (int i = 0; i < statusIds.size(); i++) {
            BoardColumn col = new BoardColumn();
            col.setId(UUID.randomUUID());
            col.setBoardId(board.getId());
            col.setStatusId(statusIds.get(i));
            col.setSortOrder(i);
            col.setCreatedAt(LocalDateTime.now());
            col.setUpdatedAt(LocalDateTime.now());
            boardColumnMapper.insert(col);
        }
    }
}
