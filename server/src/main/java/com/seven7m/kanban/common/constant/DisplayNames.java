package com.seven7m.kanban.common.constant;

/**
 * 展示层兜底名称。
 *
 * <p>「查不到关联实体时展示什么」是产品口径：附件上传人、评论/动态操作人等多处都需要同一个值。
 * 之前每个 impl 各写一份 {@code "未知"}，改口径要逐处找；收到这里统一维护。
 */
public final class DisplayNames {

    /** 关联用户已不存在/已删除时的展示名 */
    public static final String UNKNOWN_USER = "未知";

    private DisplayNames() {
    }
}
