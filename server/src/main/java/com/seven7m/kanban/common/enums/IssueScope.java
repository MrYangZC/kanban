package com.seven7m.kanban.common.enums;

/**
 * 工作项列表的"范围"筛选（对应云效看板的 All / 我负责的 / 我创建的 / 我验证的 / 共享给我 / 已归档）。
 *
 * <p>此前服务端用 {@code "ASSIGNED".equals(scope)} 这种字符串链判断，未知取值会静默落到
 * "只查未归档"的分支——前端把参数拼错（大小写、拼写）时表现为"筛选没生效"，
 * 排查成本很高。枚举化后未知取值归入 {@link #ALL}，并在解析处集中说明这一约定。
 */
public enum IssueScope {

    /** 全部（未指定或无法识别时的缺省范围）：只查未归档 */
    ALL,

    /** 我负责的 */
    ASSIGNED,

    /** 我创建的 */
    REPORTED,

    /** 我验证的 */
    VERIFIED,

    /** 共享给我的（参与人） */
    SHARED,

    /** 已归档 */
    ARCHIVED;

    /**
     * 宽松解析：未知取值一律回落到 {@link #ALL}。
     *
     * <p>不抛 400 是刻意的：前端历史版本可能传过别的词（如 "ALL"、"MINE"），
     * 抛错会让整个列表打不开；回落到缺省范围至少数据是可见的。
     */
    public static IssueScope of(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        String trimmed = value.trim();
        for (IssueScope scope : values()) {
            if (scope.name().equalsIgnoreCase(trimmed)) {
                return scope;
            }
        }
        return ALL;
    }
}
