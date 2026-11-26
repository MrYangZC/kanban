package com.seven7m.kanban.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.dashboard.DashboardVO;
import com.seven7m.kanban.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 度量页只读聚合：概览卡片、分类型概览、状态/优先级/负责人分布、周期时间、燃尽图。
 *
 * <p>要求 issue:view。口径统一在 DashboardMapper 里（排除归档、完成度按 issue_status.category、
 * 周期时间用 done_at），控制器只做转发。
 */
@RestController
@RequestMapping("/projects/{projectId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("@projectAuth.has(#projectId, 'issue:view')")
    @GetMapping
    public R<DashboardVO> getDashboard(@PathVariable UUID projectId) {
        return R.ok(dashboardService.getDashboard(projectId));
    }
}
