package com.seven7m.kanban.service.board;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.board.BoardGroupView;
import com.seven7m.kanban.dto.board.BoardView;

import java.util.UUID;

/**
 * 看板查询服务：按类别（任务/需求/缺陷）装配列与卡片，或按指定维度分组。
 *
 * <p>这里只做「读」，装配口径全部收敛在实现里；实现见
 * {@code com.seven7m.kanban.service.board.impl.BoardServiceImpl}。
 *
 * <p>不继承 {@code IService}：看板是多表聚合视图（board / board_column / workflow_status /
 * 三张工作项表），没有单一实体与单一 Mapper 的 CRUD，硬套 ServiceImpl 只会把
 * 「按类别路由」这层语义藏进继承链里。
 */
public interface BoardService {

    /** 状态列看板：列为该类别工作流节点，卡片按 statusId 归列（WIP 限制取 board_column） */
    BoardView getBoard(UUID projectId, ItemCategory category);

    /** 状态列看板，类别缺省为任务 */
    BoardView getBoard(UUID projectId);

    /**
     * 分组看板：groupBy = STATUS / ASSIGNEE / PRIORITY / SPRINT（空或未知按 STATUS）。
     * 列与交接配置一次取齐，不随分组数增长重复查询。
     */
    BoardGroupView getBoard(UUID projectId, String groupBy, ItemCategory category);

    /** 分组看板，类别缺省为任务 */
    BoardGroupView getBoard(UUID projectId, String groupBy);

    /** 类别字符串解析，非法/缺省时回退到任务类别 */
    ItemCategory resolveCategory(String categoryStr);

    /** 类别字符串解析，非法/缺省时回退到指定默认类别 */
    ItemCategory resolveCategory(String categoryStr, ItemCategory fallback);
}
