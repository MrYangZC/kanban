package com.seven7m.kanban.project.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.project.dto.CreateProjectRequest;
import com.seven7m.kanban.project.dto.ProjectVO;
import com.seven7m.kanban.project.dto.UpdateProjectRequest;
import com.seven7m.kanban.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public R<ProjectVO> create(@Valid @RequestBody CreateProjectRequest req) {
        return R.ok(projectService.create(req));
    }

    @GetMapping
    public R<List<ProjectVO>> listAll() {
        return R.ok(projectService.listAll());
    }

    @GetMapping("/{id}")
    public R<ProjectVO> get(@PathVariable UUID id) {
        return R.ok(projectService.get(id));
    }

    @PutMapping("/{id}")
    @ProjectRole // 默认 OWNER + MEMBER 可写
    public R<ProjectVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest req) {
        return R.ok(projectService.update(id, req));
    }
}
