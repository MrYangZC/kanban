package com.seven7m.kanban.common.cache;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * 请求级缓存的存储层：数据挂在当前 HTTP 请求的 attribute 上，随请求结束一起消失。
 *
 * <p>为什么不用进程内 Map（ConcurrentMap + TTL）：多实例部署时，A 节点改了成员角色，
 * B 节点的缓存最长要等 TTL 才失效——这段时间里 B 会按旧角色放行或拒绝请求，
 * 表现为"权限改了没生效 / 过一会儿又好了"。请求级缓存把这个窗口压到一次请求之内：
 * 同一请求内的重复取数只打一次库，跨请求一律重新读，天然没有跨实例陈旧问题。
 *
 * <p>没有请求上下文时（启动期、定时任务、单元测试）不做缓存：直接当作未命中，
 * {@link #put} 被忽略，调用方每次都会落到真实数据源。
 */
public final class RequestScopedCacheStore {

    /** 请求 attribute 名：整个 cacheName → (key → value) 结构挂在这一个属性下 */
    private static final String ATTRIBUTE = RequestScopedCacheStore.class.getName();

    private RequestScopedCacheStore() {
    }

    /** 取当前请求的缓存容器；无请求上下文返回 null */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<Object, Object>> container() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object existing = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing instanceof Map<?, ?>) {
            return (Map<String, Map<Object, Object>>) existing;
        }
        Map<String, Map<Object, Object>> created = new HashMap<>();
        attributes.setAttribute(ATTRIBUTE, created, RequestAttributes.SCOPE_REQUEST);
        return created;
    }

    private static Map<Object, Object> cacheOf(String cacheName, boolean create) {
        Map<String, Map<Object, Object>> container = container();
        if (container == null) {
            return null;
        }
        Map<Object, Object> cache = container.get(cacheName);
        if (cache == null && create) {
            cache = new HashMap<>();
            container.put(cacheName, cache);
        }
        return cache;
    }

    public static Object get(String cacheName, Object key) {
        Map<Object, Object> cache = cacheOf(cacheName, false);
        return cache == null ? null : cache.get(key);
    }

    public static void put(String cacheName, Object key, Object value) {
        Map<Object, Object> cache = cacheOf(cacheName, true);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    public static void evict(String cacheName, Object key) {
        Map<Object, Object> cache = cacheOf(cacheName, false);
        if (cache != null) {
            cache.remove(key);
        }
    }

    public static void clear(String cacheName) {
        Map<String, Map<Object, Object>> container = container();
        if (container != null) {
            container.remove(cacheName);
        }
    }
}
