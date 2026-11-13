package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量删除结果。
 *
 * <p>不返回"成功/失败"二值，而是把各类结果分开，前端才能给出准确反馈：
 * <ul>
 *   <li>{@code deleted} 实际删除条数（等于请求中有效且属于本项目的条数）</li>
 *   <li>{@code promoted} 因父节点被删而**上提到上一级**的子任务数量</li>
 *   <li>{@code skipped} 不属于本项目或已被他人删除而跳过的条数</li>
 *   <li>{@code failed} 删除过程报错的条数（原因见 {@code errors}，最多回传 5 条）</li>
 * </ul>
 */
@Data
public class BatchDeleteResult {

    private int deleted;
    /** 被上提的子任务数：勾了父节点却取消部分子任务时，这些子任务会被保留并上提，必须告知用户 */
    private int promoted;
    private int skipped;
    private int failed;
    /** 失败原因摘要，最多 5 条，避免大响应 */
    private List<String> errors = new ArrayList<>();

    public void addDeleted(int n) {
        deleted += n;
    }

    public void addPromoted(int n) {
        promoted += n;
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
