package com.seven7m.kanban.common.constant;

/**
 * 工作项字段的默认取值。
 *
 * <p>新建工作项时 {@code "P2"} / {@code "S3"} 这两个字面量此前直接写在业务方法里，
 * 与前端筛选面板、度量口径的默认档位靠注释对齐；抽成常量后改默认优先级只需要改这里。
 */
public final class IssueDefaults {

    /** 优先级缺省档：P0 最高，P3 最低 */
    public static final String PRIORITY = "P2";

    /** 缺陷严重级缺省档：S1 最高，S4 最低 */
    public static final String SEVERITY = "S3";

    private IssueDefaults() {
    }
}
