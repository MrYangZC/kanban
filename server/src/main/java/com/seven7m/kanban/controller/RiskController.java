package com.seven7m.kanban.controller;

import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * 风险清单：列表、详情、创建、修改、删除。
 *
 * <p>读要求 risk:view，增删改统一要求 risk:manage —— 更细的 risk:create/edit/delete
 * 从未被接口引用，已随迁移删除，避免出现「勾了也不生效」的假权限。
 */
@RestController
@RequestMapping("/projects/{projectId}/risks")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @PreAuthorize("@projectAuth.has(#projectId, 'risk:view')")
    @GetMapping
    public R<List<RiskVO>> list(@PathVariable UUID projectId) {
        return R.ok(riskService.listByProject(projectId));
    }

    @PreAuthorize("@projectAuth.has(#projectId, 'risk:view')")
    @GetMapping("/{id}")
    public R<RiskVO> detail(@PathVariable UUID projectId, @PathVariable UUID id) {
        return R.ok(riskService.detail(projectId, id));
    }

    @PreAuthorize("@projectAuth.has(#projectId, 'risk:manage')")
    @PostMapping
    public R<RiskVO> create(@PathVariable UUID projectId, @Valid @RequestBody CreateRiskRequest req) {
        return R.ok(riskService.create(projectId, req));
    }

    @PreAuthorize("@projectAuth.has(#projectId, 'risk:manage')")
    @PutMapping("/{id}")
    public R<RiskVO> update(@PathVariable UUID projectId, @PathVariable UUID id,
                            @Valid @RequestBody UpdateRiskRequest req) {
        return R.ok(riskService.update(projectId, id, req));
    }

    @PreAuthorize("@projectAuth.has(#projectId, 'risk:manage')")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable UUID projectId, @PathVariable UUID id) {
        riskService.remove(projectId, id);
        return R.ok();
    }
}
