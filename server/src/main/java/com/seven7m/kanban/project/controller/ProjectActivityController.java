package com.seven7m.kanban.project.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.project.dto.ProjectActivityVO;
import com.seven7m.kanban.project.service.ProjectActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/activities")
@RequiredArgsConstructor
public class ProjectActivityController {

    private final ProjectActivityService projectActivityService;

    @GetMapping
    public R<List<ProjectActivityVO>> list(@PathVariable UUID projectId,
                                           @RequestParam(defaultValue = "20") int limit) {
        return R.ok(projectActivityService.list(projectId, limit));
    }
}