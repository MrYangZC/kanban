package com.seven7m.kanban.issuestatus.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.issuestatus.entity.IssueStatus;
import com.seven7m.kanban.issuestatus.service.IssueStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class IssueStatusController {

    private final IssueStatusService issueStatusService;

    /** 项目状态列表（只读，供详情页状态下拉） */
    @GetMapping("/v1/projects/{projectId}/statuses")
    public R<List<IssueStatus>> projectStatuses(@PathVariable UUID projectId) {
        return R.ok(issueStatusService.listByProject(projectId));
    }
}
