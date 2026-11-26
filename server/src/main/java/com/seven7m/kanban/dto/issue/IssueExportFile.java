package com.seven7m.kanban.dto.issue;

/**
 * 工作项导出结果：文件名 + 内容 + 是否被截断 + 总命中数 + 本次实际行数，
 * 即 {@link com.seven7m.kanban.service.workitem.IssueExportService#export} 的响应体。
 *
 * <p>放在 dto 而不是内嵌进 service 接口：它是 controller 直接消费并写入响应头/响应体的数据载体，
 * 属于对外结构，应与其它 VO 同层；接口只保留契约。
 * 组件名与顺序保持不变（filename / content / truncated / total / rows），调用方读的是访问器方法，行为不变。
 */
public record IssueExportFile(String filename, byte[] content, boolean truncated, long total, int rows) {
}
