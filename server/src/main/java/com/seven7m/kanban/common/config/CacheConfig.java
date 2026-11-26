package com.seven7m.kanban.common.config;

import com.seven7m.kanban.common.cache.RequestScopedCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置：权限/角色的 {@code @Cacheable} 结果只在**当前请求内**有效。
 *
 * <p>此前用 {@code ConcurrentMapCacheManager}（进程内 Map、无 TTL）：单实例没问题，多实例部署时
 * A 节点改了成员角色，B 节点会一直按旧角色判权限（只有主动 evict 且恰好打到 A 节点才一致），
 * 表现为"授权改了没生效，重启或隔一会儿才对"。
 *
 * <p>换成 {@link RequestScopedCacheManager} 后：
 * <ul>
 *   <li>同一请求内的重复取数仍然只打一次库（列表页每行都要判权限，这个合并很重要）；</li>
 *   <li>跨请求一律重新读库，因此不存在跨实例陈旧窗口；</li>
 *   <li>写路径上的 {@code evict*} 调用仍然保留（同请求内先写后读时立即失效），语义不变。</li>
 * </ul>
 *
 * <p>新增 {@code @Cacheable/@CacheEvict} 的 cacheName 请同步登记到
 * {@link RequestScopedCacheManager} 的清单里，便于运维观测。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new RequestScopedCacheManager();
    }
}
