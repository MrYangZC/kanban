package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.ProjectMemberVO;
import com.seven7m.kanban.service.project.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    public R<List<ProjectMemberVO>> list(@PathVariable UUID projectId) {
        return R.ok(projectMemberService.list(projectId));
    }

    @ProjectRoleAnnotation(Permission.MANAGE)
    @PostMapping
    public R<ProjectMemberVO> add(@PathVariable UUID projectId,
                                  @Valid @RequestBody AddMemberRequest req) {
        return R.ok(projectMemberService.add(projectId, req));
    }

    @ProjectRoleAnnotation(Permission.MANAGE)
    @DeleteMapping("/{userId}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID userId) {
        projectMemberService.remove(projectId, userId);
        return R.ok();
    }

    @ProjectRoleAnnotation(Permission.MANAGE)
    @PutMapping("/{userId}/roles")
    public R<ProjectMemberVO> updateRoles(@PathVariable UUID projectId,
                                          @PathVariable UUID userId,
                                          @RequestBody List<String> roles) {
        return R.ok(projectMemberService.updateRoles(projectId, userId, roles));
    }
}
