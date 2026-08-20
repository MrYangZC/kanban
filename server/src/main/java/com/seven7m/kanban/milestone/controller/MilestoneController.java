package com.seven7m.kanban.milestone.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.milestone.dto.CreateMilestoneRequest;
import com.seven7m.kanban.milestone.dto.MilestoneVO;
import com.seven7m.kanban.milestone.dto.UpdateMilestoneRequest;
import com.seven7m.kanban.milestone.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @GetMapping
    public R<List<MilestoneVO>> list(@PathVariable UUID projectId) {
        return R.ok(milestoneService.list(projectId));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PostMapping
    public R<MilestoneVO> create(@PathVariable UUID projectId,
                                 @Valid @RequestBody CreateMilestoneRequest req) {
        return R.ok(milestoneService.create(projectId, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PutMapping("/{id}")
    public R<MilestoneVO> update(@PathVariable UUID projectId,
                                 @PathVariable UUID id,
                                 @RequestBody UpdateMilestoneRequest req) {
        return R.ok(milestoneService.update(projectId, id, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID projectId,
                          @PathVariable UUID id) {
        milestoneService.delete(projectId, id);
        return R.ok();
    }
}