package com.seven7m.kanban.service.board;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.board.BoardColumnView;
import com.seven7m.kanban.dto.board.BoardGroupView;
import com.seven7m.kanban.dto.board.BoardView;
import com.seven7m.kanban.dto.board.GroupColumnView;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.entity.board.Board;
import com.seven7m.kanban.entity.board.BoardColumn;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.board.BoardColumnMapper;
import com.seven7m.kanban.mapper.board.BoardMapper;
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
public class BoardService {

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
        return board == null || !StringUtils.hasText(board.getName()) ? "任务看板" : board.getName();
    }

    private String boardName(UUID projectId) {
        Board board = boardMapper.selectOne(
                new QueryWrapper<Board>().eq("project_id", projectId).last("LIMIT 1"));
        return boardNameOf(board);
    }

    private List<GroupColumnView> toColumns(List<IssueStatus> statuses, List<IssueVO> issues) {
        return statuses.stream().map(st -> {
            GroupColumnView gc = new GroupColumnView();
            gc.setStatusId(st.getId());
            gc.setName(st.getName());
            gc.setCategory(st.getCategory());
            gc.setColor(st.getColor());
            gc.setSortOrder(st.getSortOrder());
            gc.setIssues(issues.stream()
                    .filter(i -> i.getStatusId() != null && i.getStatusId().equals(st.getId()))
                    .sorted(Comparator.comparing(i -> i.getSortKey() == null ? "" : i.getSortKey()))
                    .collect(Collectors.toList()));
            return gc;
        }).collect(Collectors.toList());
    }

    private String groupKeyOf(IssueVO it, String groupBy) {
        return switch (groupBy.toUpperCase()) {
            case "ASSIGNEE" -> it.getAssigneeName() == null ? "未指派" : it.getAssigneeName();
            case "PRIORITY" -> it.getPriority() == null ? "P2" : it.getPriority();
            case "SPRINT" -> it.getSprintId() == null ? "未规划" : it.getSprintId().toString();
            default -> "全部";
        };
    }

    public BoardView getBoard(UUID projectId, ItemCategory category) {
        List<IssueVO> issues = selectForBoard(projectId, category);
        List<IssueStatus> statuses = statusesOf(projectId, category);

        Board board = boardMapper.selectOne(
                new QueryWrapper<Board>().eq("project_id", projectId).last("LIMIT 1"));
        List<BoardColumn> columns = board == null ? List.of()
                : boardColumnMapper.selectList(
                        new QueryWrapper<BoardColumn>().eq("board_id", board.getId()).orderByAsc("sort_order"));
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

    public BoardView getBoard(UUID projectId) {
        return getBoard(projectId, ItemCategory.TASK);
    }

    public BoardGroupView getBoard(UUID projectId, String groupBy, ItemCategory category) {
        List<IssueVO> issues = selectForBoard(projectId, category);
        List<IssueStatus> statuses = statusesOf(projectId, category);

        BoardGroupView view = new BoardGroupView();
        view.setName(boardName(projectId));
        view.setGroupBy(groupBy == null || groupBy.isBlank() ? "STATUS" : groupBy.toUpperCase());

        if (groupBy == null || groupBy.isBlank() || "STATUS".equalsIgnoreCase(groupBy)) {
            BoardGroupView.GroupItem all = new BoardGroupView.GroupItem();
            all.setGroupKey("ALL");
            all.setGroupName("全部");
            all.setIssueCount(issues.size());
            all.setColumns(toColumns(statuses, issues));
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
            item.setColumns(toColumns(statuses, e.getValue()));
            view.getGroups().add(item);
        }
        return view;
    }

    public BoardGroupView getBoard(UUID projectId, String groupBy) {
        return getBoard(projectId, groupBy, ItemCategory.TASK);
    }

    public ItemCategory resolveCategory(String categoryStr) {
        return resolveCategory(categoryStr, ItemCategory.TASK);
    }

    /** 类别字符串解析，非法/缺省时回退到指定默认类别 */
    public ItemCategory resolveCategory(String categoryStr, ItemCategory fallback) {
        if (!StringUtils.hasText(categoryStr)) return fallback;
        try {
            return ItemCategory.valueOf(categoryStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
