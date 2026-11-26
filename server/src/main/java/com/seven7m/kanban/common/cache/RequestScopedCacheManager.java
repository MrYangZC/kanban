package com.seven7m.kanban.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 请求级 {@link CacheManager}：所有 {@code @Cacheable} 的结果只在当前请求内有效。
 *
 * <p>替换掉原来的 {@code ConcurrentMapCacheManager}（进程内、无 TTL）：
 * 那套缓存多实例部署时改完权限要等实例各自过期，期间会出现"同一个用户在两台机器上权限不同"。
 * 权限这类数据"读多、改少、要求强一致"，用进程缓存省下的那点查询远不值得这种不可预测性；
 * 而一次请求里它可能被查十几次（列表每行都要判权限），所以请求级缓存又能实实在在省掉重复查询。
 *
 * <p>无请求上下文（定时任务、启动期）时退化为"不缓存"：每次都读真实数据源。
 */
public class RequestScopedCacheManager implements CacheManager {

    /** 显式登记缓存名：未登记的名字在 getCache 时直接建，但保留清单便于运维观测 */
    private static final List<String> CACHE_NAMES = List.of(
            "projectPermissions", "rolePermissions", "superAdminFlag", "memberRoles");

    @Override
    public Cache getCache(String name) {
        return new RequestScopedCache(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return CACHE_NAMES;
    }

    /** 单个缓存名，读写都落到 {@link RequestScopedCacheStore} */
    private static final class RequestScopedCache implements Cache {

        private final String name;

        private RequestScopedCache(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        /** 没有"原生缓存对象"可暴露：数据挂在请求上下文里 */
        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        public ValueWrapper get(Object key) {
            Object value = RequestScopedCacheStore.get(name, key);
            return value == null ? null : () -> value;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            Object value = RequestScopedCacheStore.get(name, key);
            return type != null && type.isInstance(value) ? type.cast(value) : null;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            Object cached = RequestScopedCacheStore.get(name, key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                T casted = (T) cached;
                return casted;
            }
            try {
                T loaded = valueLoader.call();
                if (loaded != null) {
                    RequestScopedCacheStore.put(name, key, loaded);
                }
                return loaded;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (value != null) {
                RequestScopedCacheStore.put(name, key, value);
            }
        }

        @Override
        public void evict(Object key) {
            RequestScopedCacheStore.evict(name, key);
        }

        @Override
        public void clear() {
            RequestScopedCacheStore.clear(name);
        }
    }
}
