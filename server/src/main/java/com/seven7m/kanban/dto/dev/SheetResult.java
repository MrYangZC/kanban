package com.seven7m.kanban.dto.dev;

import lombok.Getter;

/**
 * 单个 sheet 的导入结果，是 {@link ExcelImportResult#getSheets()} 的元素。
 *
 * <p>放在 dto 而不是内嵌进 service 接口：它随 {@link ExcelImportResult} 一起对外序列化，
 * 属于响应结构，应与其它 VO 同层；接口只保留契约。
 * 字段、getter 名与 JSON 键名均与搬家前保持一致（sheetName / projectCreated / projectKey /
 * issueCount / failCount / userCount / ok / error），前端与导出脚本不受影响。
 * {@code @Getter} 保留：Jackson 依据 getter 决定序列化键名。
 */
@Getter
public class SheetResult {
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
