package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.dto.risk.CreateRiskRequest;
import com.seven7m.kanban.dto.risk.RiskVO;
import com.seven7m.kanban.dto.risk.UpdateRiskRequest;
import com.seven7m.kanban.service.risk.RiskService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
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
@RequestMapping("/projects/{projectId}/risks")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @ProjectRoleAnnotation(Permission.RISK_VIEW)
    @GetMapping
    public R<List<RiskVO>> list(@PathVariable UUID projectId) {
        return R.ok(riskService.listByProject(projectId));
    }

    @ProjectRoleAnnotation(Permission.RISK_VIEW)
    @GetMapping("/{id}")
    public R<RiskVO> detail(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(riskService.detail(projectId, id));
    }

    @ProjectRoleAnnotation(Permission.RISK_MANAGE)
    @PostMapping
    public R<RiskVO> create(@PathVariable UUID projectId, @Valid @RequestBody CreateRiskRequest req) {
        return R.ok(riskService.create(projectId, req));
    }

    @ProjectRoleAnnotation(Permission.RISK_MANAGE)
    @PutMapping("/{id}")
    public R<RiskVO> update(@PathVariable UUID projectId, @PathVariable UUID id, @Valid @RequestBody UpdateRiskRequest req) {
        return R.ok(riskService.update(projectId, id, req));
    }

    @ProjectRoleAnnotation(Permission.RISK_MANAGE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID id) {
        riskService.remove(projectId, id);
        return R.ok();
    }
}
