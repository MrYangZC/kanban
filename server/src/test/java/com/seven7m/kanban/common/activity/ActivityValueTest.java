package com.seven7m.kanban.common.activity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活动值编解码协议的契约测试。
 *
 * <p>这套结构是"写入方拼串、读取方解析"的隐式协议，一旦编码与解码对不上，
 * 表现是详情页动态里出现 `{"status":` 之类的原始 JSON（或者"撤销交接"整体失效），
 * 而不会抛异常——所以必须固定住形状与转义。
 */
class ActivityValueTest {

    @Test
    @DisplayName("状态值编码为 {status: CODE}，空 code 返回 null")
    void encodesStatus() {
        assertThat(ActivityValue.status("REQ_TODO")).isEqualTo("{\"status\": \"REQ_TODO\"}");
        assertThat(ActivityValue.status(null)).isNull();
    }

    @Test
    @DisplayName("负责人值带 userId 时一并写入，两者都空返回 null")
    void encodesUser() {
        UUID id = UUID.randomUUID();
        assertThat(ActivityValue.user("张三", id))
                .isEqualTo("{\"user\": \"张三\", \"userId\": \"" + id + "\"}");
        assertThat(ActivityValue.user("张三", null)).isEqualTo("{\"user\": \"张三\"}");
        assertThat(ActivityValue.user(null, null)).isNull();
    }

    @Test
    @DisplayName("交接前负责人即使无人负责也要成对象，sprintId 为空写空串")
    void encodesUserWithSprint() {
        assertThat(ActivityValue.userWithSprint("张三", null, null))
                .isEqualTo("{\"user\": \"张三\", \"sprintId\": \"\"}");

        UUID sprintId = UUID.randomUUID();
        assertThat(ActivityValue.userWithSprint(null, null, sprintId))
                .isEqualTo("{\"user\": \"\", \"sprintId\": \"" + sprintId + "\"}");
    }

    @Test
    @DisplayName("引号与反斜杠必须转义，否则解析回来会丢字")
    void escapesQuotesAndBackslashes() {
        String encoded = ActivityValue.user("a\"b\\c", null);
        assertThat(ActivityValue.text(encoded, "user")).isEqualTo("a\"b\\c");
    }

    @Test
    @DisplayName("通用单字段值：null 返回 null，编码后可原样读回")
    void encodesGenericField() {
        assertThat(ActivityValue.field("label", null)).isNull();
        assertThat(ActivityValue.field("sprint", "S1")).isEqualTo("{\"sprint\": \"S1\"}");
        assertThat(ActivityValue.text(ActivityValue.field("sprint", "S1"), "sprint")).isEqualTo("S1");
    }

    @Test
    @DisplayName("读取：字段缺失返回 null，空串返回空串（两者语义不同）")
    void textDistinguishesMissingAndEmpty() {
        assertThat(ActivityValue.text("{\"user\": \"\"}", "user")).isEmpty();
        assertThat(ActivityValue.text("{\"user\": \"\"}", "userId")).isNull();
        assertThat(ActivityValue.text("not json", "user")).isNull();
        assertThat(ActivityValue.text(null, "user")).isNull();
    }

    @Test
    @DisplayName("userId / sprintId：非法值一律 null，不抛异常")
    void decodesUuidsLeniently() {
        UUID id = UUID.randomUUID();
        assertThat(ActivityValue.userId(ActivityValue.user("张三", id))).isEqualTo(id);
        assertThat(ActivityValue.userId("{\"user\": \"张三\"}")).isNull();
        assertThat(ActivityValue.userId("{\"userId\": \"not-a-uuid\"}")).isNull();
        assertThat(ActivityValue.sprintId("{\"user\": \"张三\", \"sprintId\": \"\"}")).isNull();
        assertThat(ActivityValue.sprintId("{\"sprintId\": \"" + id + "\"}")).isEqualTo(id);
    }
}
