package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.RequireAdmin;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.service.dev.ExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 一次性 Excel 数据导入接口（dev 工具，用毕请整体删除本控制器与 dev 包）。
 * 以系统管理员身份在应用上下文内直接调用 service 层批量导入，
 * 避免 2500+ 次 HTTP 调用与逐条鉴权开销。
 *
 * 调用：POST /api/v1/admin/dev/import-excel （需系统管理员权限）
 */
@RestController
@RequestMapping("/admin/dev")
@RequiredArgsConstructor
public class ExcelImportController {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String EXCEL_PATH =
            "e:/sevenM/河图/java/kanban/data/研发部工作任务管理系统.xlsx";

    private final ExcelImportService excelImportService;

    @RequireAdmin
    @PostMapping("/import-excel")
    public R<ExcelImportService.ExcelImportResult> importExcel() {
        // 以管理员身份执行，满足项目 OWNER / createdBy / 成员校验
        UserContext.set(ADMIN_ID, "admin");
        ExcelImportService.ExcelImportResult result = excelImportService.run(EXCEL_PATH);
        return R.ok(result);
    }
}
