package com.seven7m.kanban.project.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.project.dto.AddMemberRequest;
import com.seven7m.kanban.project.dto.ProjectMemberVO;
import com.seven7m.kanban.project.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    public R<List<ProjectMemberVO>> list(@PathVariable UUID projectId) {
        return R.ok(projectMemberService.list(projectId));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER})
    @PostMapping
    public R<ProjectMemberVO> add(@PathVariable UUID projectId,
                                  @Valid @RequestBody AddMemberRequest req) {
        return R.ok(projectMemberService.add(projectId, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER})
    @DeleteMapping("/{userId}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID userId) {
        projectMemberService.remove(projectId, userId);
        return R.ok();
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER})
    @PutMapping("/{userId}/role")
    public R<ProjectMemberVO> updateRole(@PathVariable UUID projectId,
                                         @PathVariable UUID userId,
                                         @RequestParam String role) {
        return R.ok(projectMemberService.updateRole(projectId, userId, role));
    }
}
