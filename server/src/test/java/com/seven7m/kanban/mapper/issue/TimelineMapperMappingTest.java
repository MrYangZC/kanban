package com.seven7m.kanban.mapper.issue;

import com.seven7m.kanban.testsupport.MapperMappingAssert;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时间线 mapper 的映射一致性测试。
 *
 * <p>把 Map 换成 POJO 之后，列名不再出现在业务代码里，但"SQL 别名"与"@Result 的 column"
 * 仍是两份需要对齐的声明：不一致不会编译报错、只会让字段安静地是 null
 * （声明了结果集里不存在的列时则会直接抛 column not found）。
 * 具体的校验规则见 {@link MapperMappingAssert}，与度量页 mapper 共用同一套断言。
 */
class TimelineMapperMappingTest {

    private static java.lang.reflect.Method method(String name, Class<?>... params) throws NoSuchMethodException {
        return IssueTimelineMapper.class.getMethod(name, params);
    }

    @Test
    @DisplayName("pageTimeline：映射列都在 SQL 别名里，实体字段都有映射")
    void pageTimelineMappingMatchesSql() throws Exception {
        MapperMappingAssert.assertMappingMatchesSql(method("pageTimeline", UUID.class, String.class, int.class, long.class));
    }

    @Test
    @DisplayName("latestHandoff：映射列都在 SQL 别名里，实体字段都有映射")
    void latestHandoffMappingMatchesSql() throws Exception {
        MapperMappingAssert.assertMappingMatchesSql(method("latestHandoff", UUID.class, String.class));
    }

    @Test
    @DisplayName("返回类型是 POJO 且实现了 toString（便于日志排查）")
    void rowTypesArePojosWithToString() {
        TimelineRow row = new TimelineRow();
        row.setType("COMMENT");
        row.setContent("hi");
        assertThat(row.toString()).contains("type=COMMENT").contains("content=hi");

        HandoffMarker marker = new HandoffMarker();
        marker.setAction(HandoffMarker.ACTION_ASSIGNEE_CHANGED);
        assertThat(marker.toString()).contains("action=ASSIGNEE_CHANGED");
        assertThat(marker.isAssigneeChanged()).isTrue();

        marker.setAction("HANDOFF_REVOKED");
        assertThat(marker.isAssigneeChanged()).isFalse();
    }

    @Test
    @DisplayName("UUID 与时间类型都有可用 TypeHandler（缺了会在运行时映射失败）")
    void typeHandlersCoverPropertyTypes() {
        // 与 application.yml 的 mybatis-plus.type-handlers-package 保持一致：
        // 项目自带的 UuidTypeHandler 在该包下，靠包扫描注册；LocalDateTime 由 MyBatis 内置。
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register("com.seven7m.kanban.common.config.handler");
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();

        assertThat(registry.hasTypeHandler(UUID.class))
                .as("uuid 列要能映射到 UUID 属性，依赖 common.config.handler.UuidTypeHandler")
                .isTrue();
        assertThat(registry.hasTypeHandler(java.time.LocalDateTime.class))
                .as("timestamptz 列要能映射到 LocalDateTime，依赖 MyBatis 内置 handler")
                .isTrue();
    }
}
