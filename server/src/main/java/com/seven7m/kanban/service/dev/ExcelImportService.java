package com.seven7m.kanban.service.dev;

import com.seven7m.kanban.dto.dev.ExcelImportResult;

/**
 * 一次性 Excel 导入编排（dev 专用）。
 * 直接复用现有 service 层，避免 2500+ 次 HTTP 调用与逐条鉴权。
 * 解析阶段用 POI 遍历 23 个 sheet，每个 sheet 建一个项目并导入任务。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.dev.impl.ExcelImportServiceImpl}，
 * 仅在 dev profile 下注册。
 *
 * <p>结果模型已从内嵌类型搬到 dto 包：汇总见 {@link ExcelImportResult}，
 * 单 sheet 见 {@link com.seven7m.kanban.dto.dev.SheetResult}；接口只保留契约。
 */
public interface ExcelImportService {

    /** 导入指定路径的 Excel：逐 sheet 建项目 + 任务，返回汇总结果；整体失败记在结果的 error 上 */
    ExcelImportResult run(String filePath);
}
