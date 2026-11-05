package com.seven7m.kanban.mapper.issue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时间线行映射的契约测试。
 *
 * <p>这层存在的意义就是把"按字符串键取 Map"换成编译期可见的属性。
 * 因此测试重点不是业务逻辑，而是：列名对不上时不崩、类型差异（UUID/字符串、Timestamp/LocalDateTime）都能吃下、
 * 缺列退化为 null 而不是抛异常。
 */
class TimelineRowTest {

    private static Map<String, Object> row() {
        Map<String, Object> row = new HashMap<>();
        row.put(TimelineRow.COL_TYPE, "COMMENT");
        row.put(TimelineRow.COL_CONTENT, "看起来不错");
        return row;
    }

    @Test
    @DisplayName("JDBC 直接给出 UUID / LocalDateTime 时按原样取用")
    void mapsNativeJdbcTypes() {
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.now();
        Map<String, Object> row = row();
        row.put(TimelineRow.COL_ID, id);
        row.put(TimelineRow.COL_ACTOR_ID, actor);
        row.put(TimelineRow.COL_PARENT_ID, parent);
        row.put(TimelineRow.COL_OLD_VALUE, "{\"status\": \"REQ_TODO\"}");
        row.put(TimelineRow.COL_NEW_VALUE, "{\"status\": \"REQ_DONE\"}");
        row.put(TimelineRow.COL_CREATED_AT, created);

        TimelineRow mapped = TimelineRow.from(row);

        assertThat(mapped.id()).isEqualTo(id);
        assertThat(mapped.actorId()).isEqualTo(actor);
        assertThat(mapped.parentId()).isEqualTo(parent);
        assertThat(mapped.createdAt()).isEqualTo(created);
        assertThat(mapped.content()).isEqualTo("看起来不错");
        assertThat(mapped.oldValue()).isEqualTo("{\"status\": \"REQ_TODO\"}");
    }

    @Test
    @DisplayName("驱动把 UUID 给成字符串、时间给成 Timestamp 时也能正确转换")
    void toleratesStringUuidAndTimestamp() {
        UUID id = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.of(2026, 11, 5, 10, 30);
        Map<String, Object> row = row();
        row.put(TimelineRow.COL_ID, id.toString());
        row.put(TimelineRow.COL_CREATED_AT, Timestamp.valueOf(created));

        TimelineRow mapped = TimelineRow.from(row);

        assertThat(mapped.id()).isEqualTo(id);
        assertThat(mapped.createdAt()).isEqualTo(created);
    }

    @Test
    @DisplayName("缺列/空值一律退化为 null，不抛异常（历史数据与不同驱动）")
    void missingColumnsDegradeToNull() {
        TimelineRow mapped = TimelineRow.from(row());

        assertThat(mapped.id()).isNull();
        assertThat(mapped.actorId()).isNull();
        assertThat(mapped.parentId()).isNull();
        assertThat(mapped.createdAt()).isNull();
        assertThat(mapped.oldValue()).isNull();
    }

    @Test
    @DisplayName("整行不存在时返回 null，调用方按空处理")
    void nullRowReturnsNull() {
        assertThat(TimelineRow.from(null)).isNull();
    }

    @Test
    @DisplayName("交接标记：只有 ASSIGNEE_CHANGED 才可撤销")
    void handoffMarkerRecognisesAssigneeChanged() {
        UUID activityId = UUID.randomUUID();
        String newValue = "{\"user\": \"李四\", \"userId\": \"" + UUID.randomUUID() + "\"}";

        HandoffMarker marker = HandoffMarker.from(Map.of(
                HandoffMarker.COL_ID, activityId,
                HandoffMarker.COL_ACTION, HandoffMarker.ACTION_ASSIGNEE_CHANGED,
                HandoffMarker.COL_NEW_VALUE, newValue));

        assertThat(marker.id()).isEqualTo(activityId);
        assertThat(marker.isAssigneeChanged()).isTrue();
        assertThat(marker.newValue()).isEqualTo(newValue);

        HandoffMarker revoked = HandoffMarker.from(Map.of(
                HandoffMarker.COL_ID, activityId,
                HandoffMarker.COL_ACTION, "HANDOFF_REVOKED",
                HandoffMarker.COL_NEW_VALUE, newValue));
        assertThat(revoked.isAssigneeChanged()).isFalse();
        assertThat(HandoffMarker.from(null)).isNull();
    }
}
