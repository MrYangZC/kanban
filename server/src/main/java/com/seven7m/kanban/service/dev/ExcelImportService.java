package com.seven7m.kanban.service.dev;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次性 Excel 导入编排（dev 专用）。
 * 直接复用现有 service 层，避免 2500+ 次 HTTP 调用与逐条鉴权。
 * 解析阶段用 POI 遍历 23 个 sheet，每个 sheet 建一个项目并导入任务。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.dev.impl.ExcelImportServiceImpl}，
 * 仅在 dev profile 下注册。
 *
 * <p>结果模型（{@link SheetResult} / {@link ExcelImportResult}）作为嵌套类型留在接口里，
 * 是为了让控制器侧的 {@code ExcelImportService.ExcelImportResult} 这一全限定名保持不变；
 * 它们只是返回值的载体，不含业务逻辑。
 */
public interface ExcelImportService {

    /** 导入指定路径的 Excel：逐 sheet 建项目 + 任务，返回汇总结果；整体失败记在结果的 error 上 */
    ExcelImportResult run(String filePath);

    /** 单个 sheet 的导入结果 */
    @Getter
    class SheetResult {
        final String sheetName;
        boolean projectCreated;
        String projectKey;
        int issueCount;
        int failCount;
        int userCount;
        boolean ok = true;
        String error;

        public SheetResult(String sheetName) {
            this.sheetName = sheetName;
        }

        public static SheetResult failed(String sheetName, String error) {
            SheetResult r = new SheetResult(sheetName);
            r.ok = false;
            r.error = error;
            return r;
        }

        public void incIssue() {
            issueCount++;
        }

        public void incFail() {
            failCount++;
        }

        public boolean projectCreated() {
            return projectCreated;
        }

        public void setProjectCreated(boolean v) {
            projectCreated = v;
        }

        public String projectKey() {
            return projectKey;
        }

        public void setProjectKey(String v) {
            projectKey = v;
        }

        public int issueCount() {
            return issueCount;
        }

        public int failCount() {
            return failCount;
        }

        public int userCount() {
            return userCount;
        }

        public void setUserCount(int v) {
            userCount = v;
        }
    }

    /** 整个文件的导入汇总 */
    class ExcelImportResult {
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
}
