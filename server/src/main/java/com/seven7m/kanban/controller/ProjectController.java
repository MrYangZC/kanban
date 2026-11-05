package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;
import com.seven7m.kanban.dto.project.CreateProjectRequest;
import com.seven7m.kanban.dto.project.ProjectCategoryCountsVO;
import com.seven7m.kanban.dto.project.ProjectFilterRequest;
import com.seven7m.kanban.dto.project.ProjectVO;
import com.seven7m.kanban.dto.project.UpdateProjectRequest;
import com.seven7m.kanban.service.project.ProjectFavoriteService;
import com.seven7m.kanban.service.project.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectFavoriteService projectFavoriteService;
    private final PermissionService permissionService;

    @PostMapping
    public R<ProjectVO> create(@Valid @RequestBody CreateProjectRequest req) {
        return R.ok(projectService.create(req));
    }

    /**
     * 查询项目列表，支持可选 category 分类筛选（向后兼容：不传时等价于 all）。
     */
    @GetMapping
    public R<List<ProjectVO>> listAll(@RequestParam(required = false) String category) {
        return R.ok(projectService.listAll(ProjectCategoryKey.of(category)));
    }

    /**
     * 分页查询项目列表，支持分类筛选、关键词搜索与排序。
     */
    @PostMapping("/filter")
    public R<PageResult<ProjectVO>> filter(@Valid @RequestBody ProjectFilterRequest req) {
        return R.ok(projectService.listByFilter(req));
    }

    /**
     * 查询当前用户五个分类的项目计数，用于分类标签栏计数展示。
     */
    @GetMapping("/categories")
    public R<ProjectCategoryCountsVO> getCategoryCounts() {
        return R.ok(projectService.getCategoryCounts());
    }

    @GetMapping("/{id}")
    public R<ProjectVO> get(@PathVariable UUID id) {
        return R.ok(projectService.get(id));
    }

    @GetMapping("/{id}/my-permissions")
    public R<Set<String>> getMyPermissions(@PathVariable UUID id) {
        return R.ok(permissionService.getPermissions(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}")
    @ProjectRoleAnnotation(Permission.PROJECT_SETTING)
    public R<ProjectVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest req) {
        return R.ok(projectService.update(id, req));
    }

    /**
     * 收藏项目（须为项目成员）。
     */
    @PostMapping("/{id}/favorite")
    public R<Void> favorite(@PathVariable UUID id) {
        projectFavoriteService.favorite(id, UserContext.getUserId());
        return R.ok();
    }

    /**
     * 取消收藏项目。
     */
    @DeleteMapping("/{id}/favorite")
    public R<Void> unfavorite(@PathVariable UUID id) {
        projectFavoriteService.unfavorite(id, UserContext.getUserId());
        return R.ok();
    }

    /**
     * 归档项目（仅 OWNER/ADMIN 可操作）。
     * 权限码用 project:archive（种子数据里只有 OWNER/ADMIN 持有），
     * 与"改项目基本信息"的 project:setting 区分开。
     */
    @PutMapping("/{id}/archive")
    @ProjectRoleAnnotation(Permission.PROJECT_ARCHIVE)
    public R<Void> archive(@PathVariable UUID id) {
        projectService.archive(id, UserContext.getUserId());
        return R.ok();
    }

    /**
     * 取消归档项目（仅 OWNER/ADMIN 可操作）。
     */
    @PutMapping("/{id}/unarchive")
    @ProjectRoleAnnotation(Permission.PROJECT_ARCHIVE)
    public R<Void> unarchive(@PathVariable UUID id) {
        projectService.unarchive(id, UserContext.getUserId());
        return R.ok();
    }

    /**
     * 删除项目（仅 OWNER/ADMIN 可操作）。
     */
    @DeleteMapping("/{id}")
    @ProjectRoleAnnotation(Permission.PROJECT_DELETE)
    public R<Void> delete(@PathVariable UUID id) {
        projectService.delete(id, UserContext.getUserId());
        return R.ok();
    }
}
