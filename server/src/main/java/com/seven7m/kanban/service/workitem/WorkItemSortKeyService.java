package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.constant.WorkItemColumns;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.util.SortKeyUtils;
import com.seven7m.kanban.entity.workitem.AbstractWorkItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * 看板列内排序键（fractional index）的维护逻辑。
 *
 * <p>从 {@code AbstractWorkItemService} 抽出来的原因：这段代码只关心"目标列里的相邻卡片 + sort_key"，
 * 与工作项的字段、权限、活动记录完全无关，却和 40 多个业务方法挤在同一个 1200 行的类里，
 * 想单独验证"键过长会重排""相邻卡片必须同列"只能启动整个 Service。
 *
 * <p>保持原有语义不变：
 * <ul>
 *   <li>落在两张卡片之间：取两者键的中间值；</li>
 *   <li>只给 before / 只给 after：在对应一侧插入；</li>
 *   <li>不给相邻卡片：追加到列尾（不能返回常量，否则与列内其它卡片并列）；</li>
 *   <li>键长度超过阈值时，先把整列改写成等宽键再重算，避免逼近 varchar(255) 上限。</li>
 * </ul>
 *
 * <p>三个工作项 Service 通过泛型 {@code T} 复用同一实现，不需要各自复制一份。
 */
@Slf4j
@Component
public class WorkItemSortKeyService {

    /**
     * sort_key 长度超过该值就触发列内重排。
     * 键是"追加字符"长出来的，长度到 80 说明该列已经被反复追加过几十次；
     * 此时既逼近 varchar(255) 上限，也容易把相邻键挤到无中间值可用。
     */
    private static final int SORT_KEY_REBALANCE_THRESHOLD = 80;

    /**
     * 目标落点：项目、列、以及可选的左右相邻卡片。
     *
     * <p>此前 {@code computeSortKey} 有 5 个参数（mapper + 4 个 id），调用处容易传错顺序；
     * 按手册"参数过多宜封装"收敛为一个值对象。
     */
    public record SortKeyRequest(UUID projectId, UUID statusId, UUID beforeId, UUID afterId) {

        /** 追加到列尾（不带相邻卡片） */
        public static SortKeyRequest appendTo(UUID projectId, UUID statusId) {
            return new SortKeyRequest(projectId, statusId, null, null);
        }
    }

    /**
     * 计算目标位置的 sort_key；结果过长时先把整列改写成等宽键再重算。
     * 重排后相邻键之间恢复大量间隔，后续插入不再退化。
     */
    public <T extends AbstractWorkItem> String computeSortKey(BaseMapper<T> mapper, SortKeyRequest request,
                                                              BiFunction<UUID, UUID, String> tailSortKey) {
        String key = computeOnce(mapper, request, tailSortKey);
        if (key != null && key.length() > SORT_KEY_REBALANCE_THRESHOLD) {
            int count = rebalanceColumn(mapper, request);
            log.info("sort_key 超长触发列内重排: project={}, status={}, rows={}",
                    request.projectId(), request.statusId(), count);
            key = computeOnce(mapper, request, tailSortKey);
        }
        return key;
    }

    private <T extends AbstractWorkItem> String computeOnce(BaseMapper<T> mapper, SortKeyRequest request,
                                                             BiFunction<UUID, UUID, String> tailSortKey) {
        UUID statusId = request.statusId();
        UUID beforeId = request.beforeId();
        UUID afterId = request.afterId();
        if (beforeId != null && afterId != null) {
            T b = mapper.selectById(beforeId);
            T a = mapper.selectById(afterId);
            if (b == null || a == null) {
                throw new ServiceException(ErrorCode.NEIGHBOR_NOT_FOUND);
            }
            ensureSameStatusColumn(statusId, b, a);
            return SortKeyUtils.between(b.getSortKey(), a.getSortKey());
        }
        if (beforeId != null) {
            T b = mapper.selectById(beforeId);
            if (b == null) {
                throw new ServiceException(ErrorCode.NEIGHBOR_NOT_FOUND);
            }
            ensureSameStatusColumn(statusId, b);
            return SortKeyUtils.between(b.getSortKey(), null);
        }
        if (afterId != null) {
            T a = mapper.selectById(afterId);
            if (a == null) {
                throw new ServiceException(ErrorCode.NEIGHBOR_NOT_FOUND);
            }
            ensureSameStatusColumn(statusId, a);
            return SortKeyUtils.between(null, a.getSortKey());
        }
        // 未指定相邻卡片：追加到目标列末尾
        return SortKeyUtils.after(tailSortKey.apply(request.projectId(), statusId));
    }



    /**
     * 把某一列的 sort_key 全部改写成等宽键（保持现有顺序）。
     * 只在 {@link #computeSortKey} 检测到键过长时触发，属于低频维护动作。
     */
    private <T extends AbstractWorkItem> int rebalanceColumn(BaseMapper<T> mapper, SortKeyRequest request) {
        List<T> column = mapper.selectList(Wrappers.<T>query()
                .eq(WorkItemColumns.PROJECT_ID, request.projectId())
                .eq(WorkItemColumns.STATUS_ID, request.statusId())
                .orderByAsc(WorkItemColumns.SORT_KEY));
        if (column.isEmpty()) {
            return 0;
        }
        List<String> keys = SortKeyUtils.rebalancedKeys(column.size());
        int updated = 0;
        for (int i = 0; i < column.size(); i++) {
            T row = column.get(i);
            String key = keys.get(i);
            if (key.equals(row.getSortKey())) {
                continue;
            }
            UpdateWrapper<T> update = Wrappers.update();
            update.eq(WorkItemColumns.ID, row.getId()).set(WorkItemColumns.SORT_KEY, key);
            mapper.update(null, update);
            updated++;
        }
        return updated;
    }

    private void ensureSameStatusColumn(UUID statusId, AbstractWorkItem... neighbors) {
        for (AbstractWorkItem n : neighbors) {
            if (!Objects.equals(statusId, n.getStatusId())) {
                throw new ServiceException(ErrorCode.NEIGHBOR_WRONG_COLUMN);
            }
        }
    }
}
