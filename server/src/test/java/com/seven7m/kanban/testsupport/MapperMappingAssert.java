package com.seven7m.kanban.testsupport;

import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

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
 * Mapper 映射一致性断言（测试支撑类）。
 *
 * <p>手写 SQL 的结果映射有两份需要对齐的声明：SQL 里的 {@code AS 别名} 与 {@code @Result(column=…)}。
 * 两者不一致不会编译失败、也不会抛异常——字段只会安静地是 null（或者运行时报
 * "column not found"）。这个工具把两类检查固化下来，供各 mapper 的测试复用：
 * <ol>
 *   <li>每个 {@code @Result} 的 column 必须真实出现在 SQL 的 AS 别名里（防拼错、防多余列）；</li>
 *   <li>返回 POJO 的每个实例字段都必须被映射（防漏映射成永远 null）。</li>
 * </ol>
 */
public final class MapperMappingAssert {

    private static final Pattern AS_ALIAS = Pattern.compile("(?i)\\bAS\\s+([a-z][a-z0-9_]*)");

    private MapperMappingAssert() {
    }

    /** 断言某查询方法的 @Results 与 SQL 别名、返回类型字段三者自洽 */
    public static void assertMappingMatchesSql(Method method) {
        Results results = method.getAnnotation(Results.class);
        ResultMap resultMap = method.getAnnotation(ResultMap.class);
        if (results == null) {
            // 复用已声明的 resultMap（@ResultMap）时，列校验由被引用的方法负责
            assertThat(resultMap)
                    .as("%s 必须显式声明 @Results 或复用 @ResultMap", method.getName())
                    .isNotNull();
            return;
        }

        Set<String> aliases = aliasesInSql(method);
        List<Result> mapped = Arrays.asList(results.value());

        assertThat(mapped).extracting(Result::column).allSatisfy(column ->
                assertThat(aliases).as("%s: SQL 里没有别名 %s", method.getName(), column).contains(column));

        Set<String> mappedProperties = new LinkedHashSet<>();
        mapped.forEach(r -> mappedProperties.add(r.property()));

        assertThat(instanceFieldNames(rowType(method)))
                .as("%s: 返回类型有字段未被映射", method.getName())
                .isSubsetOf(mappedProperties);
    }

    /** SQL 里所有 {@code AS alias} 的别名（PG 返回的列标签即小写别名） */
    public static Set<String> aliasesInSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("%s 缺少 @Select", method.getName()).isNotNull();
        Set<String> aliases = new LinkedHashSet<>();
        Matcher matcher = AS_ALIAS.matcher(String.join("\n", select.value()));
        while (matcher.find()) {
            aliases.add(matcher.group(1).toLowerCase());
        }
        return aliases;
    }

    /** 返回 POJO 类型：{@code List<T>} 取 T，单对象直接返回其类型 */
    public static Class<?> rowType(Method method) {
        java.lang.reflect.Type generic = method.getGenericReturnType();
        if (generic instanceof ParameterizedType parameterized) {
            java.lang.reflect.Type arg = parameterized.getActualTypeArguments()[0];
            if (arg instanceof ParameterizedType nested) {
                return (Class<?>) nested.getRawType();
            }
            return (Class<?>) arg;
        }
        return (Class<?>) generic;
    }

    public static Set<String> instanceFieldNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Field f : type.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                names.add(f.getName());
            }
        }
        return names;
    }
}
