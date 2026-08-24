package com.seven7m.kanban.controller.issuestatus;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.service.issuestatus.IssueStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class IssueStatusController {

    private final IssueStatusService issueStatusService;

    /** 项目状态列表（只读，供详情页状态下拉） */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{projectId}/statuses")
    public R<List<IssueStatus>> projectStatuses(@PathVariable UUID projectId) {
        return R.ok(issueStatusService.listByProject(projectId));
    }
}
