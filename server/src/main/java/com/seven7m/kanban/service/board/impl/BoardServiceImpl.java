package com.seven7m.kanban.service.board.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seven7m.kanban.common.constant.IssueDefaults;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.board.BoardColumnView;
import com.seven7m.kanban.dto.board.BoardGroupView;
import com.seven7m.kanban.dto.board.BoardView;
import com.seven7m.kanban.dto.board.GroupColumnView;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.workflow.WorkflowColumnVO;
import com.seven7m.kanban.entity.board.Board;
import com.seven7m.kanban.entity.board.BoardColumn;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.board.BoardColumnMapper;
import com.seven7m.kanban.mapper.board.BoardMapper;
import com.seven7m.kanban.service.board.BoardService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import com.seven7m.kanban.service.workitem.BugService;
import com.seven7m.kanban.service.workitem.RequirementService;
import com.seven7m.kanban.service.workitem.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 看板：按类别（缺陷/需求/任务）分区隔离取数，返回结构兼容前端 BoardGroupView 契约。
 *
 * 列来源为「该类别工作流的节点」（V31 起任务/需求/缺陷各有一套状态），
 * 不再直接取全局 issue_status——否则任务看板会列出缺陷的「已修复/暂不修复」。
 */
@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    /** 无 board 记录时回退的看板名（老项目没建 board 行，前端仍要显示一个标题） */
    private static final String DEFAULT_BOARD_NAME = "任务看板";

    /** 分组维度：按状态（缺省）；该取值下只产出一个「全部」分组 */
    private static final String GROUP_BY_STATUS = "STATUS";

    /** 分组维度：按负责人 */
    private static final String GROUP_BY_ASSIGNEE = "ASSIGNEE";

    /** 分组维度：按优先级 */
    private static final String GROUP_BY_PRIORITY = "PRIORITY";

    /** 分组维度：按迭代 */
    private static final String GROUP_BY_SPRINT = "SPRINT";

    /** 状态分组唯一分组的 key（前端按 key 识别「不分维度」） */
    private static final String GROUP_ALL_KEY = "ALL";

    /** 状态分组的展示名 */
    private static final String GROUP_ALL_NAME = "全部";

    /** 卡片负责人为空时的分组展示名 */
    private static final String UNASSIGNED_NAME = "未指派";

    /** 卡片未规划迭代时的分组展示名 */
    private static final String UNPLANNED_SPRINT_NAME = "未规划";

    /** board 表：项目列 */
    private static final String COL_PROJECT_ID = "project_id";

    /** board_column 表：所属看板列 */
    private static final String COL_BOARD_ID = "board_id";

    /** board_column 表：列排序号 */
    private static final String COL_SORT_ORDER = "sort_order";

    private final BoardMapper boardMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final RequirementService requirementService;
    private final BugService bugService;

    /**
     * 按类别（缺陷/需求/任务）分区取看板数据，杜绝跨类别混入。
     */
    private List<IssueVO> selectForBoard(UUID projectId, ItemCategory category) {
        return switch (category) {
            case TASK -> taskService.selectByProjectForBoard(projectId);
            case REQUIREMENT -> requirementService.selectByProjectForBoard(projectId);
            case BUG -> bugService.selectByProjectForBoard(projectId);
        };
    }

    /** 列状态：该类别工作流模板的节点，已按模板内顺序排好 */
    private List<IssueStatus> statusesOf(UUID projectId, ItemCategory category) {
        return workflowService.statusesOf(projectId, category);
    }

    /** 无 board 记录时回退的看板名 */
    private String boardNameOf(Board board) {
        return board == null || !StringUtils.hasText(board.getName()) ? DEFAULT_BOARD_NAME : board.getName();
    }

    /**
     * 取项目的 board 行；没有时返回 null。
     *
     * <p>不用 {@code selectOne(...).last("LIMIT 1")}：在 QueryWrapper 上拼 SQL 片段属于越层写 SQL，
     * 且不同数据库的 LIMIT 写法不一致；这里取列表再取第一条，语义与「只关心首行」等价。
     */
    private Board boardOf(UUID projectId) {
        return boardMapper.selectList(new QueryWrapper<Board>().eq(COL_PROJECT_ID, projectId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String boardName(UUID projectId) {
        return boardNameOf(boardOf(projectId));
    }

    private List<GroupColumnView> toColumns(List<WorkflowColumnVO> columns, List<IssueVO> issues) {
        return columns.stream().map(col -> {
            GroupColumnView gc = new GroupColumnView();
            gc.setStatusId(col.statusId());
            gc.setName(col.name());
            gc.setCategory(col.category());
            gc.setColor(col.color());
            gc.setSortOrder(col.sortOrder());
            if (col.handoffType() != null) gc.setHandoffType(col.handoffType());
            gc.setHandoffUserId(col.handoffUserId());
            gc.setHandoffUserName(col.handoffUserName());
            gc.setHandoffNote(col.handoffNote());
            gc.setTargetSprintId(col.targetSprintId());
            gc.setTargetSprintName(col.targetSprintName());
            gc.setTargetLabelId(col.targetLabelId());
            gc.setTargetLabelName(col.targetLabelName());
            gc.setIssues(issues.stream()
                    .filter(i -> i.getStatusId() != null && i.getStatusId().equals(col.statusId()))
                    .sorted(Comparator.comparing(i -> i.getSortKey() == null ? "" : i.getSortKey()))
                    .collect(Collectors.toList()));
            return gc;
        }).collect(Collectors.toList());
    }

    private String groupKeyOf(IssueVO it, String groupBy) {
        return switch (groupBy.toUpperCase(Locale.ROOT)) {
            case GROUP_BY_ASSIGNEE -> it.getAssigneeName() == null ? UNASSIGNED_NAME : it.getAssigneeName();
            case GROUP_BY_PRIORITY -> it.getPriority() == null ? IssueDefaults.PRIORITY : it.getPriority();
            case GROUP_BY_SPRINT -> it.getSprintId() == null ? UNPLANNED_SPRINT_NAME : it.getSprintId().toString();
            default -> GROUP_ALL_NAME;
        };
    }

    @Override
    public BoardView getBoard(UUID projectId, ItemCategory category) {
        List<IssueVO> issues = selectForBoard(projectId, category);
        List<IssueStatus> statuses = statusesOf(projectId, category);

        Board board = boardOf(projectId);
        List<BoardColumn> columns = board == null ? List.of()
                : boardColumnMapper.selectList(
                        new QueryWrapper<BoardColumn>().eq(COL_BOARD_ID, board.getId())
                                .orderByAsc(COL_SORT_ORDER));
        // board_column 只对缺陷流建过列，这里按 status_id 匹配取 WIP 限制，缺失时留空
        Map<UUID, BoardColumn> columnByStatus = columns.stream()
                .collect(Collectors.toMap(BoardColumn::getStatusId, Function.identity(), (a, b) -> a));

        List<BoardColumnView> columnViews = new ArrayList<>();
        for (IssueStatus st : statuses) {
            BoardColumn col = columnByStatus.get(st.getId());
            BoardColumnView cv = new BoardColumnView();
            cv.setId(col == null ? null : col.getId());
            cv.setStatusId(st.getId());
            cv.setName(st.getName());
            cv.setCategory(st.getCategory());
            cv.setColor(st.getColor());
            cv.setSortOrder(st.getSortOrder());
            cv.setWipLimit(col == null ? null : col.getWipLimit());
            UUID colStatus = st.getId();
            cv.setIssues(issues.stream()
                    .filter(i -> i.getStatusId() != null && i.getStatusId().equals(colStatus))
                    .sorted(Comparator.comparing(i -> i.getSortKey() == null ? "" : i.getSortKey()))
                    .collect(Collectors.toList()));
            columnViews.add(cv);
        }

        BoardView view = new BoardView();
        view.setId(board == null ? null : board.getId());
        view.setProjectId(projectId);
        // 复用上面已查出的 board，避免为取名再查一次
        view.setName(boardNameOf(board));
        view.setColumns(columnViews);
        return view;
    }

    @Override
    public BoardView getBoard(UUID projectId) {
        return getBoard(projectId, ItemCategory.TASK);
    }

    @Override
    public BoardGroupView getBoard(UUID projectId, String groupBy, ItemCategory category) {
        List<IssueVO> issues = selectForBoard(projectId, category);
        // 列与交接配置一次取齐：workflow_status 节点 + project_handoff + 相关用户各一次查询，
        // 不随列数增长
        List<WorkflowColumnVO> columns = workflowService.columnsOf(projectId, category);

        boolean statusGroup = groupBy == null || groupBy.isBlank() || GROUP_BY_STATUS.equalsIgnoreCase(groupBy);

        BoardGroupView view = new BoardGroupView();
        view.setName(boardName(projectId));
        view.setGroupBy(groupBy == null || groupBy.isBlank()
                ? GROUP_BY_STATUS
                : groupBy.toUpperCase(Locale.ROOT));

        if (statusGroup) {
            BoardGroupView.GroupItem all = new BoardGroupView.GroupItem();
            all.setGroupKey(GROUP_ALL_KEY);
            all.setGroupName(GROUP_ALL_NAME);
            all.setIssueCount(issues.size());
            all.setColumns(toColumns(columns, issues));
            view.getGroups().add(all);
            return view;
        }

        Map<String, List<IssueVO>> grouped = issues.stream()
                .collect(Collectors.groupingBy(i -> groupKeyOf(i, groupBy), LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<IssueVO>> e : grouped.entrySet()) {
            BoardGroupView.GroupItem item = new BoardGroupView.GroupItem();
            item.setGroupKey(e.getKey());
            item.setGroupName(e.getKey());
            item.setIssueCount(e.getValue().size());
            item.setColumns(toColumns(columns, e.getValue()));
            view.getGroups().add(item);
        }
        return view;
    }

    @Override
    public BoardGroupView getBoard(UUID projectId, String groupBy) {
        return getBoard(projectId, groupBy, ItemCategory.TASK);
    }

    @Override
    public ItemCategory resolveCategory(String categoryStr) {
        return resolveCategory(categoryStr, ItemCategory.TASK);
    }

    /** 类别字符串解析，非法/缺省时回退到指定默认类别 */
    @Override
    public ItemCategory resolveCategory(String categoryStr, ItemCategory fallback) {
        if (!StringUtils.hasText(categoryStr)) return fallback;
        try {
            return ItemCategory.valueOf(categoryStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
