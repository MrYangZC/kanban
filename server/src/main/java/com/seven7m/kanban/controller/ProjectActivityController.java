package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.project.ProjectActivityVO;
import com.seven7m.kanban.service.project.ProjectActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 项目动态：最近 N 条工作项变更，用于项目首页时间线。
 *
 * <p>只读，要求 issue:view；limit 有上限，避免一次拉全量历史。
 */
@RestController
@RequestMapping("/projects/{projectId}/activities")
@RequiredArgsConstructor
public class ProjectActivityController {

    private final ProjectActivityService projectActivityService;

    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping
    public R<List<ProjectActivityVO>> list(@PathVariable UUID projectId,
                                           @RequestParam(defaultValue = "20") int limit) {
        return R.ok(projectActivityService.list(projectId, limit));
    }
}