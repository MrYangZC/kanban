package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.RequireAdmin;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.service.dev.ExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 一次性 Excel 数据导入接口（dev 工具）。
 * 仅在 dev profile 下注册，生产环境不会暴露该端点；如需彻底移除，删除本控制器与 service/dev 包。
 * 以系统管理员身份在应用上下文内直接调用 service 层批量导入，
 * 避免 2500+ 次 HTTP 调用与逐条鉴权开销。
 *
 * 调用：POST /api/v1/admin/dev/import-excel （需系统管理员权限，且启用 dev profile）
 */
@RestController
@RequestMapping("/admin/dev")
@RequiredArgsConstructor
@Profile("dev")
public class ExcelImportController {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ExcelImportService excelImportService;

    /** 待导入 Excel 路径，由 kanban.dev.excel-path 注入，避免硬编码本机绝对路径 */
    @Value("${kanban.dev.excel-path:}")
    private String excelPath;

    @RequireAdmin
    @PostMapping("/import-excel")
    public R<ExcelImportService.ExcelImportResult> importExcel() {
        if (excelPath == null || excelPath.isBlank()) {
            throw new ServiceException(400, "未配置 kanban.dev.excel-path，无法执行导入");
        }
        // 以管理员身份执行，满足项目 OWNER / createdBy / 成员校验
        UserContext.set(ADMIN_ID, "admin");
        ExcelImportService.ExcelImportResult result = excelImportService.run(excelPath);
        return R.ok(result);
    }
}
