package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.milestone.CreateMilestoneRequest;
import com.seven7m.kanban.dto.milestone.MilestoneVO;
import com.seven7m.kanban.dto.milestone.UpdateMilestoneRequest;
import com.seven7m.kanban.service.milestone.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @ProjectRoleAnnotation(Permission.MILESTONE_VIEW)
    @GetMapping
    public R<List<MilestoneVO>> list(@PathVariable UUID projectId) {
        return R.ok(milestoneService.list(projectId));
    }

    @ProjectRoleAnnotation(Permission.MILESTONE_MANAGE)
    @PostMapping
    public R<MilestoneVO> create(@PathVariable UUID projectId,
                                 @Valid @RequestBody CreateMilestoneRequest req) {
        return R.ok(milestoneService.create(projectId, req));
    }

    @ProjectRoleAnnotation(Permission.MILESTONE_MANAGE)
    @PutMapping("/{id}")
    public R<MilestoneVO> update(@PathVariable UUID projectId,
                                 @PathVariable UUID id,
                                 @RequestBody UpdateMilestoneRequest req) {
        return R.ok(milestoneService.update(projectId, id, req));
    }

    @ProjectRoleAnnotation(Permission.MILESTONE_MANAGE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID projectId,
                          @PathVariable UUID id) {
        milestoneService.delete(projectId, id);
        return R.ok();
    }
}