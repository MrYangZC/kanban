package com.seven7m.kanban.dto.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seven7m.kanban.dto.issue.IssueExportFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导入/导出响应体的 JSON 契约测试。
 *
 * <p>这两个模型原来内嵌在 {@code ExcelImportService} / {@code IssueExportService} 接口里，
 * 为了不破坏响应键名才一直没搬。搬到 dto 包后，键名完全由 getter 名决定，
 * 谁顺手把 {@code ok} 改成 {@code success}、或把 record 组件改个名字，
 * 编译和启动都不会报错，前端与导出脚本却会静默拿到 {@code undefined}。
 * 这里把旧键名钉住（与 {@code UserJsonContractTest} 同一思路）。
 */
class ImportExportJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("SheetResult 的 JSON 键名与搬迁前一致")
    void sheetResultKeepsJsonKeys() {
        SheetResult sheet = new SheetResult("工作项");
        sheet.setProjectCreated(true);
        sheet.setProjectKey("KANBAN");
        sheet.incIssue();
        sheet.incFail();
        sheet.setUserCount(2);

        JsonNode node = mapper.valueToTree(sheet);

        assertThat(fieldNames(node)).containsExactlyInAnyOrder(
                "sheetName", "projectCreated", "projectKey",
                "issueCount", "failCount", "userCount", "ok", "error");
        assertThat(node.get("sheetName").asText()).isEqualTo("工作项");
        assertThat(node.get("projectCreated").asBoolean()).isTrue();
        assertThat(node.get("ok").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SheetResult.failed 的失败态仍然只靠 ok=false + error 表达")
    void failedSheetKeepsFailureShape() {
        JsonNode node = mapper.valueToTree(SheetResult.failed("坏表", "表头缺失"));

        assertThat(node.get("ok").asBoolean()).isFalse();
        assertThat(node.get("error").asText()).isEqualTo("表头缺失");
    }

    @Test
    @DisplayName("ExcelImportResult 的 JSON 键名与派生计数口径不变")
    void excelImportResultKeepsJsonKeys() {
        ExcelImportResult result = new ExcelImportResult();
        SheetResult sheet = new SheetResult("工作项");
        sheet.setProjectCreated(true);
        sheet.incIssue();
        sheet.incIssue();
        sheet.incFail();
        result.addSheet(sheet);
        result.setError("部分失败");

        JsonNode node = mapper.valueToTree(result);

        assertThat(fieldNames(node)).containsExactlyInAnyOrder(
                "sheets", "error", "totalProjects", "totalIssues", "totalFails");
        assertThat(node.get("totalProjects").asInt()).isEqualTo(1);
        assertThat(node.get("totalIssues").asInt()).isEqualTo(2);
        assertThat(node.get("totalFails").asInt()).isEqualTo(1);
        assertThat(node.get("sheets")).hasSize(1);
    }

    @Test
    @DisplayName("IssueExportFile 是 record：组件名即 JSON 键名")
    void issueExportFileKeepsComponentNames() {
        IssueExportFile file = new IssueExportFile("k.csv", "id,title\n".getBytes(), true, 500L, 100);

        JsonNode node = mapper.valueToTree(file);

        assertThat(fieldNames(node)).containsExactlyInAnyOrder(
                "filename", "content", "truncated", "total", "rows");
        assertThat(node.get("truncated").asBoolean()).isTrue();
        assertThat(node.get("rows").asInt()).isEqualTo(100);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
