package com.seven7m.kanban.service.board;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.board.BoardColumnView;
import com.seven7m.kanban.dto.board.BoardGroupView;
import com.seven7m.kanban.dto.board.BoardView;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.entity.board.Board;
import com.seven7m.kanban.entity.board.BoardColumn;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.board.BoardColumnMapper;
import com.seven7m.kanban.mapper.board.BoardMapper;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
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

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardMapper boardMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final TaskService taskService;
    private final RequirementService requirementService;
    private final BugService bugService;

    /**
     * 按类别（缺陷/需求/任务）分区隔离的看板。
     * 不传 category 时默认返回 TASK，避免任何跨类别数据混入。
     */
    private List<IssueVO> selectForBoard(UUID projectId, ItemCategory category) {
        return switch (category) {
            case TASK -> taskService.selectByProjectForBoard(projectId);
            case REQUIREMENT -> requirementService.selectByProjectForBoard(projectId);
            case BUG -> bugService.selectByProjectForBoard(projectId);
        };
    }

    public BoardView getBoard(UUID projectId, ItemCategory category) {
        List<IssueVO> issues = selectForBoard(projectId, category);
        Map<UUID, List<IssueVO>> byStatus = issues.stream()
                .collect(Collectors.groupingBy(i -> i.getStatusId() == null ? null : UUID.fromString(i.getStatusId().toString())));

        Board board = boardMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Board>().eq("project_id", projectId).last("LIMIT 1"));
        List<BoardColumn> columns = board == null ? List.of()
                : boardColumnMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BoardColumn>().eq("board_id", board.getId())
                                .orderByAsc("sort_order"));

        List<BoardColumnView> columnViews = new ArrayList<>();
        for (BoardColumn col : columns) {
            List<IssueVO> colIssues = byStatus.getOrDefault(col.getStatusId(), List.of());
            columnViews.add(new BoardColumnView(col.getStatusId(), colIssues));
        }
        BoardView view = new BoardView();
        view.setColumns(columnViews);
        return view;
    }

    public BoardView getBoard(UUID projectId) {
        return getBoard(projectId, ItemCategory.TASK);
    }

    public BoardGroupView getBoard(UUID projectId, String groupBy, ItemCategory category) {
        List<IssueVO> issues = selectForBoard(projectId, category);
        Map<String, List<IssueVO>> grouped;
        if (groupBy == null) groupBy = "STATUS";

        switch (groupBy.toUpperCase()) {
            case "ASSIGNEE" -> grouped = issues.stream()
                    .collect(Collectors.groupingBy(i -> i.getAssigneeName() == null ? "未指派" : i.getAssigneeName(),
                            LinkedHashMap::new, Collectors.toList()));
            case "PRIORITY" -> grouped = issues.stream()
                    .collect(Collectors.groupingBy(i -> i.getPriority() == null ? "P2" : i.getPriority(),
                            LinkedHashMap::new, Collectors.toList()));
            case "SPRINT" -> grouped = issues.stream()
                    .collect(Collectors.groupingBy(i -> i.getSprintId() == null ? "未规划" : i.getSprintId().toString(),
                            LinkedHashMap::new, Collectors.toList()));
            case "STATUS":
            default -> grouped = issues.stream()
                    .collect(Collectors.groupingBy(i -> i.getStatusId() == null ? "UNKNOWN" : i.getStatusId().toString(),
                            LinkedHashMap::new, Collectors.toList()));
        }

        List<IssueStatus> statuses = issueStatusMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IssueStatus>().eq("project_id", projectId));
        Map<String, IssueStatus> statusMap = statuses.stream()
                .collect(Collectors.toMap(s -> s.getId().toString(), Function.identity(), (a, b) -> a));

        BoardGroupView view = new BoardGroupView();
        List<BoardGroupView.Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<IssueVO>> e : grouped.entrySet()) {
            BoardGroupView.Group g = new BoardGroupView.Group();
            g.setKey(e.getKey());
            IssueStatus st = statusMap.get(e.getKey());
            g.setName(st == null ? e.getKey() : st.getName());
            g.setColor(st == null ? null : st.getColor());
            g.setIssues(e.getValue());
            groups.add(g);
        }
        groups.sort(Comparator.comparing(BoardGroupView.Group::getKey));
        view.setGroups(groups);
        return view;
    }

    public BoardGroupView getBoard(UUID projectId, String groupBy) {
        return getBoard(projectId, groupBy, ItemCategory.TASK);
    }

    public ItemCategory resolveCategory(String categoryStr) {
        if (!StringUtils.hasText(categoryStr)) return ItemCategory.TASK;
        try {
            return ItemCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ItemCategory.TASK;
        }
    }
}
