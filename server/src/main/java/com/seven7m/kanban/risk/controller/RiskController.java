package com.seven7m.kanban.risk.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.risk.dto.CreateRiskRequest;
import com.seven7m.kanban.risk.dto.RiskVO;
import com.seven7m.kanban.risk.dto.UpdateRiskRequest;
import com.seven7m.kanban.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/risks")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @GetMapping
    public R<List<RiskVO>> list(@PathVariable UUID projectId) {
        return R.ok(riskService.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public R<RiskVO> detail(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(riskService.detail(projectId, id));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM, com.seven7m.kanban.project.ProjectRole.DEV, com.seven7m.kanban.project.ProjectRole.QA, com.seven7m.kanban.project.ProjectRole.MEMBER})
    @PostMapping
    public R<RiskVO> create(@PathVariable UUID projectId, @RequestBody CreateRiskRequest req) {
        return R.ok(riskService.create(projectId, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM, com.seven7m.kanban.project.ProjectRole.DEV, com.seven7m.kanban.project.ProjectRole.QA, com.seven7m.kanban.project.ProjectRole.MEMBER})
    @PutMapping("/{id}")
    public R<RiskVO> update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody UpdateRiskRequest req) {
        return R.ok(riskService.update(projectId, id, req));
    }

    @ProjectRole(required = {com.seven7m.kanban.project.ProjectRole.OWNER, com.seven7m.kanban.project.ProjectRole.ADMIN, com.seven7m.kanban.project.ProjectRole.PM})
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID id) {
        riskService.remove(projectId, id);
        return R.ok();
    }
}
