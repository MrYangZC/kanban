package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.workitem.AbstractWorkItem;

import java.util.UUID;
import java.util.function.Function;

/**
 * 工作项父级校验。
 *
 * <p>支持通过接口设置 parentId 之后，"父级是谁"变成外部输入，必须挡住三类脏数据：
 * <ul>
 *   <li><b>跨表</b>：任务挂到缺陷下——树形视图里点不开，批量删除时也会串类别；</li>
 *   <li><b>跨项目</b>：父级属于别的项目——权限校验只看子项所在项目，会越权看到别的项目标题；</li>
 *   <li><b>成环</b>：A 挂到 B、B 又挂到 A——树形渲染无限递归，批量删除沿着父子链死循环。</li>
 * </ul>
 *
 * <p>抽成独立类而不是 Service 私有方法，一是为了能单独测试，
 * 二是 task/requirement/bug 三个 Service 共用同一套规则（避免三份实现漂移）。
 */
public final class WorkItemParentGuard {

    /** 上溯父链的最大步数：正常层级远小于它，超过即视为数据成环 */
    private static final int MAX_DEPTH = 100;

    private WorkItemParentGuard() {
    }

    /**
     * @param selfId    更新场景传自身 id；创建场景传 null
     * @param parentId  目标父级
     * @param projectId 期望所属项目
     * @param loader    按 id 查同表工作项（传 {@code mapper()::selectById} 即可，
     *                  因为是同表查询，跨类别会自然查不到）
     */
    public static <T extends AbstractWorkItem> void requireValid(
            UUID selfId, UUID parentId, UUID projectId, Function<UUID, T> loader) {
        if (parentId == null) {
            return;
        }
        T parent = loader.apply(parentId);
        if (parent == null) {
            throw new ServiceException(ErrorCode.PARENT_NOT_FOUND);
        }
        if (projectId != null && !projectId.equals(parent.getProjectId())) {
            throw new ServiceException(ErrorCode.PARENT_CROSS_PROJECT);
        }
        if (selfId == null) {
            return;
        }
        if (selfId.equals(parentId)) {
            throw new ServiceException(ErrorCode.PARENT_IS_SELF);
        }
        UUID cursor = parent.getParentId();
        int guard = 0;
        while (cursor != null) {
            if (cursor.equals(selfId)) {
                throw new ServiceException(ErrorCode.PARENT_IS_DESCENDANT);
            }
            T next = loader.apply(cursor);
            cursor = next == null ? null : next.getParentId();
            if (++guard > MAX_DEPTH) {
                throw new ServiceException(ErrorCode.PARENT_CYCLE);
            }
        }
    }
}
