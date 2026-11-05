package com.seven7m.kanban.controller;

import com.seven7m.kanban.dto.board.BoardGroupView;
import com.seven7m.kanban.dto.board.BoardView;
import com.seven7m.kanban.service.board.BoardService;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 看板视图：用 board:view（此前借用了 issue:view，导致 board:view 定义了却从未生效）。
     * 种子里所有能看工作项的角色都持有 board:view，因此不会收窄可见范围。
     */
    @ProjectRoleAnnotation(Permission.BOARD_VIEW)
    @GetMapping("/{projectId}/board")
    public R<BoardView> getBoard(@PathVariable UUID projectId,
                                @RequestParam(required = false) String category) {
        return R.ok(boardService.getBoard(projectId, boardService.resolveCategory(category)));
    }

    /** 分组看板：groupBy=STATUS/ASSIGNEE/PRIORITY/SPRINT（缺省 STATUS） */
    @ProjectRoleAnnotation(Permission.BOARD_VIEW)
    @GetMapping("/{projectId}/board/grouped")
    public R<BoardGroupView> getBoardGrouped(@PathVariable UUID projectId,
                                             @RequestParam(required = false) String groupBy,
                                             @RequestParam(required = false) String category) {
        return R.ok(boardService.getBoard(projectId, groupBy, boardService.resolveCategory(category)));
    }
}
