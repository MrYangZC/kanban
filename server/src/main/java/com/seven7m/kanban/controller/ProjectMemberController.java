package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.MemberCandidateVO;
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

    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping
    public R<List<ProjectMemberVO>> list(@PathVariable UUID projectId) {
        return R.ok(projectMemberService.list(projectId));
    }

    /**
     * 「添加成员」的可选用户（未加入本项目的用户），支持账号/显示名搜索。
     * 不用 GET /users：那接口只对系统管理员开放，项目管理员加成员时会 403。
     */
    @ProjectRoleAnnotation(Permission.PROJECT_MEMBER)
    @GetMapping("/candidates")
    public R<List<MemberCandidateVO>> candidates(@PathVariable UUID projectId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return R.ok(projectMemberService.candidates(projectId, keyword, limit));
    }

    @ProjectRoleAnnotation(Permission.PROJECT_MEMBER)
    @PostMapping
    public R<ProjectMemberVO> add(@PathVariable UUID projectId,
                                  @Valid @RequestBody AddMemberRequest req) {
        return R.ok(projectMemberService.add(projectId, req));
    }

    @ProjectRoleAnnotation(Permission.PROJECT_MEMBER)
    @DeleteMapping("/{userId}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID userId) {
        projectMemberService.remove(projectId, userId);
        return R.ok();
    }

    @ProjectRoleAnnotation(Permission.PROJECT_MEMBER)
    @PutMapping("/{userId}/roles")
    public R<ProjectMemberVO> updateRoles(@PathVariable UUID projectId,
                                          @PathVariable UUID userId,
                                          @RequestBody List<String> roles) {
        return R.ok(projectMemberService.updateRoles(projectId, userId, roles));
    }
}
