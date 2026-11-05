package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.sprint.CreateSprintRequest;
import com.seven7m.kanban.dto.sprint.SprintVO;
import com.seven7m.kanban.dto.sprint.UpdateSprintRequest;
import com.seven7m.kanban.service.sprint.SprintService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
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

/**
 * 迭代：列表、详情、创建、修改、删除，以及开始/完成两个状态动作。
 *
 * <p>读要求 sprint:view，写要求 sprint:manage。开始/完成会改迭代状态与日期，
 * 因此走独立的 POST 子路径而不是 PATCH 字段。
 */
@RestController
@RequestMapping("/projects/{projectId}/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @ProjectRoleAnnotation(Permission.SPRINT_VIEW)
    @GetMapping
    public R<List<SprintVO>> list(@PathVariable UUID projectId) {
        return R.ok(sprintService.listByProject(projectId));
    }

    @ProjectRoleAnnotation(Permission.SPRINT_VIEW)
    @GetMapping("/{id}")
    public R<SprintVO> detail(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(sprintService.detail(projectId, id));
    }

    @ProjectRoleAnnotation(Permission.SPRINT_MANAGE)
    @PostMapping
    public R<SprintVO> create(@PathVariable UUID projectId, @Valid @RequestBody CreateSprintRequest req) {
        return R.ok(sprintService.create(projectId, req));
    }

    @ProjectRoleAnnotation(Permission.SPRINT_MANAGE)
    @PutMapping("/{id}")
    public R<SprintVO> update(@PathVariable UUID projectId, @PathVariable UUID id,
                              @Valid @RequestBody UpdateSprintRequest req) {
        return R.ok(sprintService.update(projectId, id, req));
    }

    @ProjectRoleAnnotation(Permission.SPRINT_MANAGE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID id) {
        sprintService.remove(projectId, id);
        return R.ok();
    }

    @ProjectRoleAnnotation(Permission.SPRINT_MANAGE)
    @PostMapping("/{id}/start")
    public R<SprintVO> start(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(sprintService.start(projectId, id));
    }

    @ProjectRoleAnnotation(Permission.SPRINT_MANAGE)
    @PostMapping("/{id}/complete")
    public R<SprintVO> complete(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(sprintService.complete(projectId, id));
    }
}
