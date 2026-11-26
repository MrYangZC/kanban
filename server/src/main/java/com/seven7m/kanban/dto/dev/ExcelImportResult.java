package com.seven7m.kanban.dto.dev;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 一次性导入的汇总结果，即
 * {@link com.seven7m.kanban.service.dev.ExcelImportService#run(String)} 的响应体。
 *
 * <p>放在 dto 而不是内嵌进 service 接口：它是对外返回的数据载体（Jackson 按 getter 直接序列化），
 * 与 dto 下其它 VO 同层；接口只保留契约，不再混入数据模型与展示层注解。
 * 字段、getter 名与 JSON 键名均与搬家前保持一致（sheets / error / totalProjects / totalIssues / totalFails），
 * 前端与导出脚本不受影响。
 */
public class ExcelImportResult {

    final List<SheetResult> sheets = new ArrayList<>();
    String error;
    int totalProjects;
    int totalIssues;
    int totalFails;

    public void addSheet(SheetResult sr) {
        sheets.add(sr);
        if (sr.projectCreated) {
            totalProjects++;
        }
        totalIssues += sr.issueCount;
        totalFails += sr.failCount;
    }

    public void setError(String e) {
        error = e;
    }

    public List<SheetResult> getSheets() {
        return sheets;
    }

    public String getError() {
        return error;
    }

    public int getTotalProjects() {
        return totalProjects;
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    public int getTotalFails() {
        return totalFails;
    }
}
