package com.seven7m.kanban.service.issue.comment;

import com.seven7m.kanban.common.activity.ActivityValue;
import com.seven7m.kanban.common.domain.NameIndex;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把 `issue_activity` 里存的机器值翻译成中文展示文本。
 *
 * <p>动态的 `old_value` / `new_value` 落库时是 `{"status": "REQ_TODO"}` 这种状态 code
 * （见 {@code AbstractWorkItemService#jsonStatus}），原样返回前端就会显示成英文 code，
 * 用户在「动态 / 评论」里看到的是 `{"status":"REQ_TODO"}` 这类内容，完全读不懂。
 * 因此在接口装配层统一翻成状态名（「待处理」「已取消」…），前端不必再维护一份字典。
 *
 * <p>只做展示翻译：库里仍保留 code，历史数据无需迁移；遇到非 status 结构的旧值原样返回。
 * 交接（ASSIGNEE_CHANGED）存的是 `{"user": "张三"}`，值本身已是显示名，直接透出。
 */
@Component
@RequiredArgsConstructor
public class ActivityTextResolver {

    /** 动态值里的协议字段名（与 {@link ActivityValue} 的写入侧同一份协议） */
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_USER = "user";
    private static final String FIELD_SPRINT = "sprint";
    private static final String FIELD_LABEL = "label";

    /** 状态字典的预期规模（全局十几个节点），仅用于预分配，避免逐次扩容 */
    private static final int STATUS_DICT_EXPECTED_SIZE = 16;

    /**
     * 字典里已不存在、但历史动态里仍可能出现的 code → 中文名。
     * V33 改过需求流节点（REQ_TO_REVIEW → REQ_TODO、REQ_SCHEDULED → REQ_DESIGNING），
     * REQ_REJECTED（已拒绝）节点被直接移除；存量数据虽已由 V36 改写，这里再兜一层，
     * 避免换库/回滚后动态又冒出英文 code。
     */
    private static final NameIndex<String> LEGACY_STATUS_NAMES = NameIndex.of(Map.of(
            "REQ_TO_REVIEW", "待处理",
            "REQ_SCHEDULED", "设计中",
            "REQ_REJECTED", "已拒绝"
    ));

    private final IssueStatusMapper issueStatusMapper;

    /**
     * 载入「状态 code → 中文名」字典。
     * 单次请求只查一次（全局 16 条状态），不要放进逐条循环里。
     *
     * <p>返回 {@link NameIndex} 而不是 {@code Map<String, String>}：调用方只应"按键取名称"，
     * 不应看到（也不应依赖）底层容器；缺失键统一由 {@code nameOf} 返回 null 再兜底，
     * 避免每个调用点各写一遍 {@code get} + 判空。
     */
    public NameIndex<String> loadStatusNames() {
        List<IssueStatus> all = issueStatusMapper.selectList(null);
        Map<String, String> dict = new HashMap<>(Math.max(STATUS_DICT_EXPECTED_SIZE, all.size() * 2));
        for (IssueStatus s : all) {
            if (s.getCode() == null || s.getCode().isBlank()) {
                continue;
            }
            dict.putIfAbsent(s.getCode(), displayNameOf(s));
        }
        return NameIndex.of(dict);
    }

    /** 状态名的兜底口径：名称为空时退回 code，至少让用户看到一个可辨认的标识 */
    private static String displayNameOf(IssueStatus status) {
        String name = status.getName();
        return name == null || name.isBlank() ? status.getCode() : name;
    }

    /**
     * 单值翻译：`{"status": "REQ_TODO"}` → `待处理`，`{"user": "张三"}` → `张三`；
     * 其他结构原样返回。
     *
     * @param raw         库里存的原值（可能为 null）
     * @param statusNames {@link #loadStatusNames()} 的结果
     */
    public String toText(String raw, NameIndex<String> statusNames) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 结构解析统一走 ActivityValue（与写入方同一份协议实现）
        if (ActivityValue.parse(raw) == null) {
            return raw;
        }
        // 交接动态（ASSIGNEE_CHANGED）落库时已是显示名，不需要再查字典
        String user = ActivityValue.text(raw, FIELD_USER);
        if (user != null) {
            return user;
        }
        // 交接落点（SPRINT_CHANGED / LABEL_ADDED）同理，存的就是迭代名/标签名
        String sprint = ActivityValue.text(raw, FIELD_SPRINT);
        if (sprint != null) {
            return sprint;
        }
        String label = ActivityValue.text(raw, FIELD_LABEL);
        if (label != null) {
            return label;
        }
        String code = ActivityValue.text(raw, FIELD_STATUS);
        if (code == null) {
            return raw;
        }
        String name = statusNames.nameOf(code);
        if (name == null || name.isBlank()) {
            name = LEGACY_STATUS_NAMES.nameOf(code);
        }
        return name == null || name.isBlank() ? code : name;
    }

    /**
     * 取动态值里携带的 userId（只有交接动态 {@code {"user":..,"userId":..}} 才有）。
     * 交接功能上线前的历史记录只存了显示名，取不到返回 null，调用方按「不可撤销」处理，
     * 不要退回姓名去反查用户 —— 显示名可重复。
     */
    public UUID userIdOf(String raw) {
        return ActivityValue.userId(raw);
    }
}
