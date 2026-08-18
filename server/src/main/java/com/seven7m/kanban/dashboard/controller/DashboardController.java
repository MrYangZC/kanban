package com.seven7m.kanban.dashboard.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.dashboard.dto.DashboardVO;
import com.seven7m.kanban.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public R<DashboardVO> getDashboard(@PathVariable UUID projectId) {
        return R.ok(dashboardService.getDashboard(projectId));
    }
}
