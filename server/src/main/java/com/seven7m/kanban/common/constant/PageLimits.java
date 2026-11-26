package com.seven7m.kanban.common.constant;

/**
 * 分页与批量查询的边界值。
 *
 * <p>这些数字此前以字面量形式出现在每个 Service 里（{@code size < 1 ? 20 : Math.min(size, 100)}、
 * {@code ? 1000 : Math.min(..., 1000)}），改一处忘一处就会出现"某个列表页能翻 1000 条、
 * 另一个只能翻 100 条"的不一致。
 */
public final class PageLimits {

    /** 最小页码 */
    public static final int MIN_PAGE = 1;

    /** 管理类列表（用户、角色等）缺省每页条数 */
    public static final int ADMIN_DEFAULT_SIZE = 20;

    /** 管理类列表每页上限 */
    public static final int ADMIN_MAX_SIZE = 100;

    /** 工作项列表缺省每页条数（前端看板一次性拉全量，故缺省较大） */
    public static final int LIST_DEFAULT_SIZE = 1000;

    /** 工作项列表每页上限 */
    public static final int LIST_MAX_SIZE = 1000;

    private PageLimits() {
    }
}
