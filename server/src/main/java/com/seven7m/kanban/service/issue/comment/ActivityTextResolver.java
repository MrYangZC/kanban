package com.seven7m.kanban.service.issue.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 `issue_activity` 里存的机器值翻译成中文展示文本。
 *
 * <p>动态的 `old_value` / `new_value` 落库时是 `{"status": "REQ_TODO"}` 这种状态 code
 * （见 {@code BaseWorkItemService#jsonStatus}），原样返回前端就会显示成英文 code，
 * 用户在「动态 / 评论」里看到的是 `{"status":"REQ_TODO"}` 这类内容，完全读不懂。
 * 因此在接口装配层统一翻成状态名（「待处理」「已取消」…），前端不必再维护一份字典。
 *
 * <p>只做展示翻译：库里仍保留 code，历史数据无需迁移；遇到非 status 结构的旧值原样返回。
 */
@Component
@RequiredArgsConstructor
public class ActivityTextResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 字典里已不存在、但历史动态里仍可能出现的 code → 中文名。
     * V33 改过需求流节点（REQ_TO_REVIEW → REQ_TODO、REQ_SCHEDULED → REQ_DESIGNING），
     * REQ_REJECTED（已拒绝）节点被直接移除；存量数据虽已由 V36 改写，这里再兜一层，
     * 避免换库/回滚后动态又冒出英文 code。
     */
    private static final Map<String, String> LEGACY_STATUS_NAMES = Map.of(
            "REQ_TO_REVIEW", "待处理",
            "REQ_SCHEDULED", "设计中",
            "REQ_REJECTED", "已拒绝"
    );

    private final IssueStatusMapper issueStatusMapper;

    /**
     * 载入「状态 code → 中文名」字典。
     * 单次请求只查一次（全局 16 条状态），不要放进逐条循环里。
     */
    public Map<String, String> loadStatusNames() {
        List<IssueStatus> all = issueStatusMapper.selectList(null);
        Map<String, String> dict = new HashMap<>(Math.max(16, all.size() * 2));
        for (IssueStatus s : all) {
            if (s.getCode() == null || s.getCode().isBlank()) {
                continue;
            }
            dict.putIfAbsent(s.getCode(), s.getName() == null || s.getName().isBlank() ? s.getCode() : s.getName());
        }
        return dict;
    }

    /**
     * 单值翻译：`{"status": "REQ_TODO"}` → `待处理`；其他结构原样返回。
     *
     * @param raw         库里存的原值（可能为 null）
     * @param statusNames {@link #loadStatusNames()} 的结果
     */
    public String toText(String raw, Map<String, String> statusNames) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = statusCode(raw);
        if (code == null) {
            return raw;
        }
        String name = statusNames.get(code);
        if (name == null || name.isBlank()) {
            name = LEGACY_STATUS_NAMES.get(code);
        }
        return name == null || name.isBlank() ? code : name;
    }

    /** 从 `{"status": "CODE"}` 中取出 code；结构不符返回 null */
    private String statusCode(String raw) {
        String text = raw.trim();
        if (!text.startsWith("{")) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(text);
            JsonNode status = node.get("status");
            if (status == null || status.isNull()) {
                return null;
            }
            String code = status.asText();
            return code == null || code.isBlank() ? null : code;
        } catch (Exception e) {
            return null;
        }
    }
}
