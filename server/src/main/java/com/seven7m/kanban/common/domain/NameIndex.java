package com.seven7m.kanban.common.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 名称索引：把"一次批量查出来的 id → 名称"封装成有类型的只读查询对象。
 *
 * <p>为什么不用 {@code Map<K, String>}：方法签名返回 Map 时，调用方拿到的是一个
 * "键是什么、值是什么、缺失怎么处理"都要靠猜的裸容器——手册也明确不允许把 Map
 * 当作查询结果返回。这里只暴露三个语义化操作（取名称 / 是否存在 / 是否为空），
 * 内部结构对外不可见，后续换实现（比如带默认值的兜底）不必改调用点。
 *
 * <p>典型用法：先 {@code selectList(...in(ids))} 批量取，再
 * {@link #of(Map)} 建索引，最后在 VO 装配循环里 {@code nameOf(id)}，避免 N+1 查询。
 */
public final class NameIndex<K> {

    private static final NameIndex<?> EMPTY = new NameIndex<>(Map.of());

    private final Map<K, String> names;

    private NameIndex(Map<K, String> names) {
        this.names = names;
    }

    /** 空索引：批量查询没有命中任何记录时使用，调用方不必判空 */
    @SuppressWarnings("unchecked")
    public static <K> NameIndex<K> empty() {
        return (NameIndex<K>) EMPTY;
    }

    /** 用已有映射建索引（调用方负责去重与空值处理） */
    public static <K> NameIndex<K> of(Map<K, String> names) {
        if (names == null || names.isEmpty()) {
            return empty();
        }
        return new NameIndex<>(Collections.unmodifiableMap(new LinkedHashMap<>(names)));
    }

    /** 取名称；不存在返回 null（调用方决定展示"未知"还是留空） */
    public String nameOf(K key) {
        return key == null ? null : names.get(key);
    }

    /** 是否存在该键：用于"引用还在不在"的降级判断（例如交接落点已被删除） */
    public boolean contains(K key) {
        return key != null && names.containsKey(key);
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public int size() {
        return names.size();
    }
}
