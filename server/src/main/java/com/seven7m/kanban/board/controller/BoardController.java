package com.seven7m.kanban.board.controller;

import com.seven7m.kanban.board.dto.BoardGroupView;
import com.seven7m.kanban.board.dto.BoardView;
import com.seven7m.kanban.board.service.BoardService;
import com.seven7m.kanban.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/{projectId}/board")
    public R<BoardView> getBoard(@PathVariable UUID projectId) {
        return R.ok(boardService.getBoard(projectId));
    }

    /** 分组看板：groupBy=STATUS/ASSIGNEE/PRIORITY/SPRINT（缺省 STATUS） */
    @GetMapping("/{projectId}/board/grouped")
    public R<BoardGroupView> getBoardGrouped(@PathVariable UUID projectId,
                                             @RequestParam(required = false) String groupBy) {
        return R.ok(boardService.getBoard(projectId, groupBy));
    }
}
