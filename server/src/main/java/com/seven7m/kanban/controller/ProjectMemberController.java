package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.MemberCandidateVO;
import com.seven7m.kanban.dto.project.ProjectMemberVO;
import com.seven7m.kanban.service.project.ProjectMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 项目成员管理：花名册、候选用户检索、增删成员、改角色。
 *
 * <p>读写门槛不同：读只要 issue:view（成员都能看花名册），写要 project:member。
 * 成员角色变化会改变权限判定结果，因此服务层同时清掉该项目的权限码缓存。
 */
@Validated
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
                                          @NotEmpty(message = "至少指定一个角色") @RequestBody List<String> roles) {
        return R.ok(projectMemberService.updateRoles(projectId, userId, roles));
    }
}
