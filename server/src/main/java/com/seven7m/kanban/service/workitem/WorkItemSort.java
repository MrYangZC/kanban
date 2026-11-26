package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seven7m.kanban.common.constant.WorkItemColumns;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.issue.IssueVO;

import java.util.Comparator;
import java.util.function.Function;

/**
 * 工作项排序规则的**唯一事实源**：同一份声明同时给出 SQL 排序片段与内存 {@link Comparator}。
 *
 * <p>背景：单表查询走 SQL（{@code AbstractWorkItemService#listByFilter}），跨表合并（"全部工作项"视图）
 * 走内存排序（{@code WorkItemRouter#comparatorFor}）。此前两套实现各写一遍，注释里只能靠一句
 * "需与 SQL 保持一致"来约束，实际已经漂移：
 * <ul>
 *   <li>SQL 对非缺陷表按 {@code severity} 排序会回落到 {@code status_id, sort_key}，内存实现却一律按 severity 排；</li>
 *   <li>分页没有稳定兜底列，排序值相同的行在两次查询间可能换位，深页出现重复/漏项。</li>
 * </ul>
 * 现在改一个字段只需改这里一处，两边自动一致。
 */
public final class WorkItemSort {

    /** 升序/降序参数取值：前端 query 里的 sortOrder，服务层不再到处写 "asc"/"desc" 字面量 */
    public static final String ASC = "asc";
    public static final String DESC = "desc";

    /**
     * 编号的自然序排序表达式。
     *
     * <p>{@code issue_number} 是 varchar，直接按字典序排会出现 "1000" 排在 "999" 前面；
     * 先比长度再比字典序等价于数字自然序。这是本类唯一允许出现的 SQL 片段——
     * 它属于排序规则的一部分（与内存 Comparator 成对维护），不是业务 where 条件。
     */
    private static final String ISSUE_NUMBER_LENGTH = "LENGTH(" + WorkItemColumns.ISSUE_NUMBER + ")";

    private WorkItemSort() {
    }

    /** 受支持的排序字段（未知值一律回落到默认排序，防止 SQL 注入与拼写错误） */
    public enum Field {
        DEFAULT, ISSUE_KEY, UPDATED_AT, CREATED_AT, PRIORITY, SEVERITY, STATUS_ID, SORT_KEY;

        static Field of(String sortField) {
            if (sortField == null || sortField.isBlank()) {
                return DEFAULT;
            }
            return switch (sortField) {
                case "issueKey" -> ISSUE_KEY;
                case "updatedAt" -> UPDATED_AT;
                case "createdAt" -> CREATED_AT;
                case "priority" -> PRIORITY;
                case "severity" -> SEVERITY;
                case "statusId" -> STATUS_ID;
                case "sortKey" -> SORT_KEY;
                default -> DEFAULT;
            };
        }
    }

    /**
     * 把排序规则写入 SQL。
     *
     * @param category 当前表对应的类别：只有缺陷表存在有意义的 severity，
     *                 其他类别按"状态 + sort_key"兜底（与内存排序的规则一致）
     */
    public static void applySql(QueryWrapper<?> q, String sortField, String sortOrder, ItemCategory category) {
        boolean asc = !DESC.equalsIgnoreCase(sortOrder);
        switch (Field.of(sortField)) {
            case ISSUE_KEY -> {
                // issue_number 是 varchar：先比长度再比字典序，等价于数字自然序（避免 "1000" 排在 "999" 前）
                if (asc) {
                    q.orderByAsc(ISSUE_NUMBER_LENGTH).orderByAsc(WorkItemColumns.ISSUE_NUMBER);
                } else {
                    q.orderByDesc(ISSUE_NUMBER_LENGTH).orderByDesc(WorkItemColumns.ISSUE_NUMBER);
                }
            }
            case UPDATED_AT -> order(q, WorkItemColumns.UPDATED_AT, asc);
            case CREATED_AT -> order(q, WorkItemColumns.CREATED_AT, asc);
            case PRIORITY -> order(q, WorkItemColumns.PRIORITY, asc);
            case SEVERITY -> {
                if (category == ItemCategory.BUG) {
                    order(q, WorkItemColumns.SEVERITY, asc);
                } else {
                    defaultOrder(q);
                }
            }
            case STATUS_ID -> order(q, WorkItemColumns.STATUS_ID, asc);
            case SORT_KEY -> order(q, WorkItemColumns.SORT_KEY, asc);
            case DEFAULT -> defaultOrder(q);
        }
        // 稳定兜底：排序值相同的行必须有确定顺序，否则分页在两次查询之间会抖动
        q.orderByAsc(WorkItemColumns.ID);
    }

    /** 内存排序（升序）。调用方需要降序时用 {@code comparator.reversed()}，与 Postgres 的 NULLS 语义一致 */
    public static Comparator<IssueVO> comparator(String sortField) {
        return switch (Field.of(sortField)) {
            case ISSUE_KEY -> Comparator.comparing(IssueVO::getIssueNumber,
                    Comparator.nullsLast(WorkItemSort::compareIssueNumber));
            case UPDATED_AT -> comparing(IssueVO::getUpdatedAt);
            case CREATED_AT -> comparing(IssueVO::getCreatedAt);
            case PRIORITY -> comparing(IssueVO::getPriority);
            case SEVERITY -> severityThenDefault();
            case STATUS_ID -> comparing(IssueVO::getStatusId);
            case SORT_KEY -> comparing(IssueVO::getSortKey);
            case DEFAULT -> defaultOrder();
        };
    }

    private static void order(QueryWrapper<?> q, String column, boolean asc) {
        if (asc) {
            q.orderByAsc(column);
        } else {
            q.orderByDesc(column);
        }
    }

    private static void defaultOrder(QueryWrapper<?> q) {
        q.orderByAsc("status_id").orderByAsc("sort_key");
    }

    private static Comparator<IssueVO> defaultOrder() {
        return comparing(IssueVO::getStatusId)
                .thenComparing(comparing(IssueVO::getSortKey))
                .thenComparing(comparing(IssueVO::getId));
    }

    /**
     * severity 只在缺陷上有意义：有值的（缺陷）按 severity 排，没值的按默认规则排在后面，
     * 与 SQL 里"非缺陷表回落 status_id, sort_key"的意图对齐。
     */
    private static Comparator<IssueVO> severityThenDefault() {
        return comparing(IssueVO::getSeverity).thenComparing(defaultOrder());
    }

    private static <V extends Comparable<? super V>> Comparator<IssueVO> comparing(Function<IssueVO, V> key) {
        return Comparator.comparing(key, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /** issue_number 为数字串：按长度 + 字典序比较，与 SQL 的 LENGTH(issue_number), issue_number 一致 */
    static int compareIssueNumber(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);
    }
}
