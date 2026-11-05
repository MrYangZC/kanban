package com.seven7m.kanban.mapper.issue;

import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时间线 mapper 的"映射与 SQL 一致性"测试。
 *
 * <p>把 Map 换成 POJO 之后，列名不再出现在业务代码里，但"SQL 别名"与"@Result 的 column"
 * 仍然是两份需要对齐的声明。这类不一致不会编译报错、也不会抛异常，只会让字段安静地是 null。
 * 因此这里直接读注解做静态校验：
 * <ol>
 *   <li>每个 @Result 的 column 必须真的出现在 SQL 的 AS 别名里（防拼错）；</li>
 *   <li>返回类型上的每个实例字段都必须被映射（防漏映射）。</li>
 * </ol>
 */
class TimelineMapperMappingTest {

    /** 匹配 SQL 里的 `xxx AS alias`，PG 返回的列标签即小写别名 */
    private static final Pattern AS_ALIAS = Pattern.compile("(?i)\\bAS\\s+([a-z][a-z0-9_]*)");

    private static Method method(String name, Class<?>... params) throws NoSuchMethodException {
        return IssueTimelineMapper.class.getMethod(name, params);
    }

    private static Set<String> aliasesInSql(Method m) {
        Select select = m.getAnnotation(Select.class);
        assertThat(select).as("@Select 缺失").isNotNull();
        String sql = String.join("\n", select.value());
        Set<String> aliases = new LinkedHashSet<>();
        Matcher matcher = AS_ALIAS.matcher(sql);
        while (matcher.find()) {
            aliases.add(matcher.group(1).toLowerCase());
        }
        return aliases;
    }

    private static Class<?> rowType(Method m) {
        java.lang.reflect.Type generic = m.getGenericReturnType();
        assertThat(generic).isInstanceOf(ParameterizedType.class);
        return (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
    }

    private static List<Result> resultsOf(Method m) {
        Results results = m.getAnnotation(Results.class);
        assertThat(results).as("必须显式声明 @Results，禁止依赖自动映射").isNotNull();
        return Arrays.asList(results.value());
    }

    private static Set<String> instanceFieldNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Field f : type.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                names.add(f.getName());
            }
        }
        return names;
    }

    @Test
    @DisplayName("pageTimeline：映射列都在 SQL 别名里，实体字段都有映射")
    void pageTimelineMappingMatchesSql() throws Exception {
        Method m = method("pageTimeline", java.util.UUID.class, String.class, int.class, long.class);
        Set<String> aliases = aliasesInSql(m);
        List<Result> results = resultsOf(m);

        // 1) @Result 的 column 不能凭空出现
        assertThat(results).extracting(Result::column).allSatisfy(column ->
                assertThat(aliases).as("SQL 里没有别名 %s", column).contains(column));

        // 2) 实体字段必须全部有映射，否则该字段永远是 null
        Set<String> mapped = new LinkedHashSet<>();
        results.forEach(r -> mapped.add(r.property()));
        assertThat(instanceFieldNames(rowType(m))).as("未映射的字段")
                .isSubsetOf(mapped);
    }

    @Test
    @DisplayName("latestHandoff：映射列都在 SQL 别名里，实体字段都有映射")
    void latestHandoffMappingMatchesSql() throws Exception {
        Method m = method("latestHandoff", java.util.UUID.class, String.class);
        Set<String> aliases = aliasesInSql(m);
        List<Result> results = resultsOf(m);

        assertThat(results).extracting(Result::column).allSatisfy(column ->
                assertThat(aliases).as("SQL 里没有别名 %s", column).contains(column));

        Set<String> mapped = new LinkedHashSet<>();
        results.forEach(r -> mapped.add(r.property()));
        assertThat(instanceFieldNames(HandoffMarker.class)).isSubsetOf(mapped);
    }

    @Test
    @DisplayName("返回类型是两个 POJO 且实现了 toString（便于日志排查）")
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

        assertThat(registry.hasTypeHandler(java.util.UUID.class))
                .as("uuid 列要能映射到 UUID 属性，依赖 common.config.handler.UuidTypeHandler")
                .isTrue();
        assertThat(registry.hasTypeHandler(java.time.LocalDateTime.class))
                .as("timestamptz 列要能映射到 LocalDateTime，依赖 MyBatis 内置 handler")
                .isTrue();
    }
}
