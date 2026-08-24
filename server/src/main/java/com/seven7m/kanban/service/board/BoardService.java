package com.seven7m.kanban.service.board;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.dto.board.BoardColumnView;
import com.seven7m.kanban.dto.board.BoardGroupView;
import com.seven7m.kanban.dto.board.BoardView;
import com.seven7m.kanban.dto.board.GroupColumnView;
import com.seven7m.kanban.entity.board.Board;
import com.seven7m.kanban.entity.board.BoardColumn;
import com.seven7m.kanban.mapper.board.BoardColumnMapper;
import com.seven7m.kanban.mapper.board.BoardMapper;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.entity.issue.Issue;
import com.seven7m.kanban.mapper.issue.IssueMapper;
import com.seven7m.kanban.service.issue.IssueService;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardMapper boardMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final ProjectMapper projectMapper;
    private final IssueService issueService;
    private final SysUserMapper sysUserMapper;

    /**
     * 看板聚合：一次 SQL 拉全量未删除 Issue，Java 内存按 board_column.status_id 分组。
     */
    public BoardView getBoard(UUID projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeleted() != null && project.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        Board board = boardMapper.selectOne(Wrappers.<Board>lambdaQuery()
                .eq(Board::getProjectId, projectId)
                .orderByAsc(Board::getId).last("LIMIT 1"));
        if (board == null) {
            throw new ServiceException(404, "项目尚未初始化看板");
        }

        // 列（含 status 信息）
        List<BoardColumn> columns = boardColumnMapper.selectList(Wrappers.<BoardColumn>lambdaQuery()
                .eq(BoardColumn::getBoardId, board.getId())
                .orderByAsc(BoardColumn::getSortOrder));

        // 一次拉取全量 Issue 并分组
        List<Issue> issues = issueMapper.selectByProjectForBoard(projectId);
        Map<UUID, List<Issue>> byStatus = issues.stream()
                .collect(Collectors.groupingBy(Issue::getStatusId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, IssueStatus> statusMap = issueStatusMapper.selectList(Wrappers.<IssueStatus>lambdaQuery())
                .stream().collect(Collectors.toMap(IssueStatus::getId, s -> s));

        List<BoardColumnView> columnViews = new ArrayList<>();
        for (BoardColumn col : columns) {
            BoardColumnView cv = new BoardColumnView();
            BeanUtils.copyProperties(col, cv);
            IssueStatus st = statusMap.get(col.getStatusId());
            if (st != null) {
                cv.setName(st.getName());
                cv.setCategory(st.getCategory());
                cv.setColor(st.getColor());
            }
            List<Issue> colIssues = byStatus.getOrDefault(col.getStatusId(), List.of());
            cv.setIssues(colIssues.stream()
                    .map(i -> issueService.toVO(i, project.getProjectKey()))
                    .collect(Collectors.toList()));
            columnViews.add(cv);
        }

        BoardView view = new BoardView();
        view.setId(board.getId());
        view.setProjectId(projectId);
        view.setName(board.getName());
        view.setColumns(columnViews);
        return view;
    }

    /**
     * 分组（Swimlane）看板：在状态列之上按 groupBy 维度（ASSIGNEE/PRIORITY/SPRINT）再切分。
     * 沿用既有「一次 SQL + 内存分组」策略，不新增数据库查询。
     */
    public BoardGroupView getBoard(UUID projectId, String groupBy) {
        if (groupBy == null || groupBy.isBlank() || "STATUS".equalsIgnoreCase(groupBy)) {
            BoardView v = getBoard(projectId);
            BoardGroupView gv = new BoardGroupView();
            gv.setGroupBy("STATUS");
            gv.setName(v.getName());
            BoardGroupView.GroupItem all = new BoardGroupView.GroupItem();
            all.setGroupKey("ALL");
            all.setGroupName("全部");
            all.setIssueCount(v.getColumns().stream().mapToLong(c -> c.getIssues().size()).sum());
            all.setColumns(v.getColumns().stream().map(c -> {
                GroupColumnView gc = new GroupColumnView();
                gc.setStatusId(c.getStatusId());
                gc.setName(c.getName());
                gc.setCategory(c.getCategory());
                gc.setColor(c.getColor());
                gc.setSortOrder(c.getSortOrder());
                gc.setIssues(c.getIssues());
                return gc;
            }).collect(Collectors.toList()));
            gv.getGroups().add(all);
            return gv;
        }

        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeleted() != null && project.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        Board board = boardMapper.selectOne(Wrappers.<Board>lambdaQuery()
                .eq(Board::getProjectId, projectId)
                .orderByAsc(Board::getId).last("LIMIT 1"));
        if (board == null) {
            throw new ServiceException(404, "项目尚未初始化看板");
        }

        List<BoardColumn> columns = boardColumnMapper.selectList(Wrappers.<BoardColumn>lambdaQuery()
                .eq(BoardColumn::getBoardId, board.getId())
                .orderByAsc(BoardColumn::getSortOrder));
        Map<UUID, IssueStatus> statusMap = issueStatusMapper.selectList(Wrappers.<IssueStatus>lambdaQuery())
                .stream().collect(Collectors.toMap(IssueStatus::getId, s -> s));
        Map<UUID, String> userMap = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery())
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));

        List<Issue> issues = issueMapper.selectByProjectForBoard(projectId);

        // 按 groupBy 计算分组 key
        Map<String, List<Issue>> byGroup = new LinkedHashMap<>();
        for (Issue it : issues) {
            String key = groupKeyOf(it, groupBy, userMap);
            byGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
        }

        BoardGroupView view = new BoardGroupView();
        view.setName(board.getName());
        view.setGroupBy(groupBy.toUpperCase());
        for (Map.Entry<String, List<Issue>> e : byGroup.entrySet()) {
            BoardGroupView.GroupItem item = new BoardGroupView.GroupItem();
            item.setGroupKey(e.getKey());
            item.setGroupName(e.getKey());
            item.setIssueCount(e.getValue().size());
            item.setColumns(columns.stream().map(col -> {
                GroupColumnView gc = new GroupColumnView();
                gc.setStatusId(col.getStatusId());
                IssueStatus st = statusMap.get(col.getStatusId());
                if (st != null) {
                    gc.setName(st.getName());
                    gc.setColor(st.getColor());
                    gc.setCategory(st.getCategory());
                }
                gc.setSortOrder(col.getSortOrder());
                List<Issue> colIssues = e.getValue().stream()
                        .filter(i -> i.getStatusId().equals(col.getStatusId()))
                        .sorted((a, b) -> compareSortKey(a.getSortKey(), b.getSortKey()))
                        .collect(Collectors.toList());
                gc.setIssues(colIssues.stream()
                        .map(i -> issueService.toVO(i, project.getProjectKey()))
                        .collect(Collectors.toList()));
                return gc;
            }).collect(Collectors.toList()));
            view.getGroups().add(item);
        }
        return view;
    }

    private String groupKeyOf(Issue it, String groupBy, Map<UUID, String> userMap) {
        switch (groupBy.toUpperCase()) {
            case "ASSIGNEE":
                if (it.getAssigneeId() == null) return "未分配";
                return userMap.getOrDefault(it.getAssigneeId(), "未分配");
            case "PRIORITY":
                return it.getPriority() == null ? "NONE" : it.getPriority();
            case "SPRINT":
                return it.getSprintId() == null ? "未分配" : "Sprint";
            default:
                return "ALL";
        }
    }

    private int compareSortKey(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.compareTo(b);
    }
}
