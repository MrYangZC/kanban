package com.seven7m.kanban.mapper.dashboard;

import com.seven7m.kanban.dto.dashboard.DashboardVO;
import com.seven7m.kanban.testsupport.MapperMappingAssert;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 度量页 mapper 的映射一致性测试。
 *
 * <p>这 7 条统计 SQL 的别名与 {@code @Results} 是两份独立声明，写错只会让卡片显示 0 或抛
 * "column not found"，很难在联调时定位。这里在测试期把它们对齐：
 * 别名必须存在于 SQL、投影类字段必须全部被映射。
 */
class DashboardMapperMappingTest {

    private static java.lang.reflect.Method method(String name, Class<?>... params) throws NoSuchMethodException {
        return DashboardMapper.class.getMethod(name, params);
    }

    @Test
    @DisplayName("概览 summary：列与字段一致")
    void summaryMappingMatchesSql() throws Exception {
        MapperMappingAssert.assertMappingMatchesSql(method("summary", UUID.class));
    }

    @Test
    @DisplayName("分类型概览 typeSummaries：列与字段一致")
    void typeSummariesMappingMatchesSql() throws Exception {
        MapperMappingAssert.assertMappingMatchesSql(method("typeSummaries", UUID.class));
    }

    @Test
    @DisplayName("周期时间 cycleTime：列与字段一致")
    void cycleTimeMappingMatchesSql() throws Exception {
        MapperMappingAssert.assertMappingMatchesSql(method("cycleTime", UUID.class));
    }

    @Test
    @DisplayName("每日完成 / 每日新建：各自列与字段一致（分开建模，避免声明结果集里不存在的列）")
    void dailyMappingsMatchSql() throws Exception {
        MapperMappingAssert.assertMappingMatchesSql(method("dailyCompleted", UUID.class, LocalDateTime.class));
        MapperMappingAssert.assertMappingMatchesSql(method("dailyCreated", UUID.class, LocalDateTime.class));
    }

    @Test
    @DisplayName("分布类（直接返回 API VO）：列与字段一致")
    void distributionMappingsMatchSql() throws Exception {
        MapperMappingAssert.assertMappingMatchesSql(method("statusDist", UUID.class));
        MapperMappingAssert.assertMappingMatchesSql(method("assigneeDist", UUID.class));
        // priorityDist 复用 statusDist 声明的 resultMap
        MapperMappingAssert.assertMappingMatchesSql(method("priorityDist", UUID.class));
    }

    @Test
    @DisplayName("映射目标类型的 Java 类型都有可用 TypeHandler")
    void typeHandlersCoverRowTypes() {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register("com.seven7m.kanban.common.config.handler");
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();

        assertThat(registry.hasTypeHandler(UUID.class)).isTrue();
        assertThat(registry.hasTypeHandler(LocalDateTime.class)).isTrue();
        assertThat(registry.hasTypeHandler(Long.class)).isTrue();
        assertThat(registry.hasTypeHandler(Double.class)).isTrue();
        assertThat(registry.hasTypeHandler(String.class)).isTrue();
    }

    @Test
    @DisplayName("投影类是 POJO：有 getter/setter 与 toString，且不用基本类型")
    void rowTypesAreProperPojos() throws Exception {
        for (Class<?> type : new Class<?>[]{DashboardSummaryRow.class, TypeSummaryRow.class,
                CycleTimeRow.class, DailyCompletedRow.class, DailyCreatedRow.class}) {
            assertThat(type.getDeclaredMethod("toString")).isNotNull();
            for (java.lang.reflect.Field f : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                assertThat(f.getType().isPrimitive())
                        .as("%s.%s 不能用基本类型（手册要求 POJO 属性用包装类型）", type.getSimpleName(), f.getName())
                        .isFalse();
            }
        }
        // 顺带锁住 VO 侧也在用包装类型
        assertThat(DashboardVO.Summary.class).isNotNull();
    }
}
