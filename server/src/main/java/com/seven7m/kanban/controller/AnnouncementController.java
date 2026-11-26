package com.seven7m.kanban.controller;

import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * 项目公告：列表、发布、修改、删除（含置顶）。
 *
 * <p>读要求 issue:view，写要求 announcement:manage。
 */
@RestController
@RequestMapping("/projects/{projectId}/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PreAuthorize("@projectAuth.has(#projectId, 'issue:view')")
    @GetMapping
    public R<List<AnnouncementVO>> list(@PathVariable UUID projectId) {
        return R.ok(announcementService.list(projectId));
    }

    @PreAuthorize("@projectAuth.has(#projectId, 'announcement:manage')")
    @PostMapping
    public R<AnnouncementVO> create(@PathVariable UUID projectId,
                                    @Valid @RequestBody CreateAnnouncementRequest req) {
        return R.ok(announcementService.create(projectId, req));
    }

    @PreAuthorize("@projectAuth.has(#projectId, 'announcement:manage')")
    @PutMapping("/{id}")
    public R<AnnouncementVO> update(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody UpdateAnnouncementRequest req) {
        return R.ok(announcementService.update(projectId, id, req));
    }

    @PreAuthorize("@projectAuth.has(#projectId, 'announcement:manage')")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID projectId,
                          @PathVariable UUID id) {
        announcementService.delete(projectId, id);
        return R.ok();
    }
}