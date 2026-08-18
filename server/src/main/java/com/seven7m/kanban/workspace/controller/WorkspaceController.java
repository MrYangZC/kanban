package com.seven7m.kanban.workspace.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.workspace.dto.CreateWorkspaceRequest;
import com.seven7m.kanban.workspace.dto.WorkspaceVO;
import com.seven7m.kanban.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public R<WorkspaceVO> create(@Valid @RequestBody CreateWorkspaceRequest req) {
        return R.ok(workspaceService.create(req));
    }

    @GetMapping
    public R<List<WorkspaceVO>> listAll() {
        return R.ok(workspaceService.listAll());
    }

    @GetMapping("/{id}")
    public R<WorkspaceVO> get(@PathVariable UUID id) {
        return R.ok(workspaceService.get(id));
    }
}
