package com.seven7m.kanban.announcement.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.announcement.dto.AnnouncementVO;
import com.seven7m.kanban.announcement.dto.CreateAnnouncementRequest;
import com.seven7m.kanban.announcement.dto.UpdateAnnouncementRequest;
import com.seven7m.kanban.announcement.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public R<List<AnnouncementVO>> list(@PathVariable UUID projectId) {
        return R.ok(announcementService.list(projectId));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PostMapping
    public R<AnnouncementVO> create(@PathVariable UUID projectId,
                                    @Valid @RequestBody CreateAnnouncementRequest req) {
        return R.ok(announcementService.create(projectId, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @PutMapping("/{id}")
    public R<AnnouncementVO> update(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @RequestBody UpdateAnnouncementRequest req) {
        return R.ok(announcementService.update(projectId, id, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID projectId,
                          @PathVariable UUID id) {
        announcementService.delete(projectId, id);
        return R.ok();
    }
}