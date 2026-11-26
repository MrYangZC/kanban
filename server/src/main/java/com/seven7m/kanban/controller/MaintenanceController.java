package com.seven7m.kanban.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.upload.UploadMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 运维维护接口（仅系统管理员）。
 * 目前只有"清理孤儿上传文件"：历史遗留的、已无人引用的附件文件。
 */
@RestController
@RequestMapping("/admin/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final UploadMaintenanceService uploadMaintenanceService;

    /**
     * 清理上传目录中未被引用的孤儿文件。
     *
     * @param dryRun      true（默认）只统计不删除
     * @param minAgeHours 只清理超过该小时数的文件，避免误删"刚上传、记录还没落库"的在途文件
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/uploads/cleanup")
    public R<UploadMaintenanceService.CleanupResult> cleanupUploads(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "24") int minAgeHours) {
        int hours = Math.max(1, Math.min(minAgeHours, 24 * 30));
        return R.ok(uploadMaintenanceService.cleanupOrphans(dryRun, Duration.ofHours(hours)));
    }
}
