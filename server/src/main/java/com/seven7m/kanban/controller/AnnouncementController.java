package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.announcement.AnnouncementVO;
import com.seven7m.kanban.dto.announcement.CreateAnnouncementRequest;
import com.seven7m.kanban.dto.announcement.UpdateAnnouncementRequest;
import com.seven7m.kanban.service.announcement.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public R<List<AnnouncementVO>> list(@PathVariable UUID projectId) {
        return R.ok(announcementService.list(projectId));
    }

    @ProjectRoleAnnotation(Permission.MANAGE)
    @PostMapping
    public R<AnnouncementVO> create(@PathVariable UUID projectId,
                                    @Valid @RequestBody CreateAnnouncementRequest req) {
        return R.ok(announcementService.create(projectId, req));
    }

    @ProjectRoleAnnotation(Permission.MANAGE)
    @PutMapping("/{id}")
    public R<AnnouncementVO> update(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @RequestBody UpdateAnnouncementRequest req) {
        return R.ok(announcementService.update(projectId, id, req));
    }

    @ProjectRoleAnnotation(Permission.MANAGE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID projectId,
                          @PathVariable UUID id) {
        announcementService.delete(projectId, id);
        return R.ok();
    }
}