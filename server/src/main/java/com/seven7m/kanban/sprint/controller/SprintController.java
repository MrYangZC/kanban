package com.seven7m.kanban.sprint.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.sprint.dto.CreateSprintRequest;
import com.seven7m.kanban.sprint.dto.SprintVO;
import com.seven7m.kanban.sprint.dto.UpdateSprintRequest;
import com.seven7m.kanban.sprint.service.SprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @GetMapping
    public R<List<SprintVO>> list(@PathVariable UUID projectId) {
        return R.ok(sprintService.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public R<SprintVO> detail(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(sprintService.detail(projectId, id));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PostMapping
    public R<SprintVO> create(@PathVariable UUID projectId, @RequestBody CreateSprintRequest req) {
        return R.ok(sprintService.create(projectId, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PutMapping("/{id}")
    public R<SprintVO> update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody UpdateSprintRequest req) {
        return R.ok(sprintService.update(projectId, id, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID id) {
        sprintService.remove(projectId, id);
        return R.ok();
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PostMapping("/{id}/start")
    public R<SprintVO> start(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(sprintService.start(projectId, id));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PostMapping("/{id}/complete")
    public R<SprintVO> complete(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(sprintService.complete(projectId, id));
    }
}
