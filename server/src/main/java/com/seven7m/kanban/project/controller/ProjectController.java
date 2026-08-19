package com.seven7m.kanban.project.controller;

import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.project.ProjectCategoryKey;
import com.seven7m.kanban.project.dto.CreateProjectRequest;
import com.seven7m.kanban.project.dto.ProjectCategoryCountsVO;
import com.seven7m.kanban.project.dto.ProjectFilterRequest;
import com.seven7m.kanban.project.dto.ProjectVO;
import com.seven7m.kanban.project.dto.UpdateProjectRequest;
import com.seven7m.kanban.project.service.ProjectFavoriteService;
import com.seven7m.kanban.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectFavoriteService projectFavoriteService;

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
    public R<PageResult<ProjectVO>> filter(@RequestBody ProjectFilterRequest req) {
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

    @PutMapping("/{id}")
    @ProjectRole
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
     */
    @PutMapping("/{id}/archive")
    public R<Void> archive(@PathVariable UUID id) {
        projectService.archive(id, UserContext.getUserId());
        return R.ok();
    }

    /**
     * 取消归档项目（仅 OWNER/ADMIN 可操作）。
     */
    @PutMapping("/{id}/unarchive")
    public R<Void> unarchive(@PathVariable UUID id) {
        projectService.unarchive(id, UserContext.getUserId());
        return R.ok();
    }
}
