package com.seven7m.kanban.dto.issue;

import lombok.Data;

/**
 * 批量删除结果。
 *
 * <p>不返回"成功/失败"二值，而是把三类结果分开，前端才能给出准确反馈：
 * <ul>
 *   <li>{@code deleted} 实际删除条数（含级联删除的子任务）</li>
 *   <li>{@code skipped} 不属于本项目或已被他人删除而跳过的条数</li>
 *   <li>{@code failed} 删除过程报错的条数（原因见 {@code errors}，最多回传前 5 条）</li>
 * </ul>
 */
@Data
public class BatchDeleteResult {

    private int deleted;
    private int skipped;
    private int failed;
    /** 失败原因摘要，最多 5 条，避免大响应 */
    private java.util.List<String> errors = new java.util.ArrayList<>();

    public void incDeleted() {
        deleted++;
    }

    public void incSkipped() {
        skipped++;
    }

    public void addFailure(String message) {
        failed++;
        if (errors.size() < 5) {
            errors.add(message);
        }
    }
}
