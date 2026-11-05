package com.seven7m.kanban.mapper.issue;

import java.util.Map;
import java.util.UUID;
/**
 * 最近一条交接动态的投影：只用于判断"撤销交接"是否可用。
 *
 * <p>与 {@link TimelineRow} 同源（同一张 {@code issue_activity} 表），但只需要三列，
 * 单独成类型避免调用方再从 Map 里按字符串取键。
 */
public record HandoffMarker(UUID id, String action, String newValue) {

    public static final String COL_ID = "id";
    public static final String COL_ACTION = "action";
    public static final String COL_NEW_VALUE = "new_value";

    /** 交接动态的动作码：与 {@code BaseWorkItemService} 写入值保持一致 */
    public static final String ACTION_ASSIGNEE_CHANGED = "ASSIGNEE_CHANGED";

    public static HandoffMarker from(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object id = row.get(COL_ID);
        Object action = row.get(COL_ACTION);
        Object newValue = row.get(COL_NEW_VALUE);
        return new HandoffMarker(
                id == null ? null : UUID.fromString(id.toString()),
                action == null ? null : action.toString(),
                newValue == null ? null : newValue.toString());
    }

    /** 这条动态是否是"负责人变更"（撤销的前提） */
    public boolean isAssigneeChanged() {
        return ACTION_ASSIGNEE_CHANGED.equals(action);
    }
}
