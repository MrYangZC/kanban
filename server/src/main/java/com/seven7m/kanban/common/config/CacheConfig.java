package com.seven7m.kanban.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 进程内缓存管理：支撑 PermissionService 的权限结果缓存。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 注意：ConcurrentMapCacheManager 使用显式名字构造时处于非动态模式，
     * 未在此注册的缓存名会在首次 getCache 时抛
     * IllegalArgumentException("Cannot find cache named ...")。
     * 因此新增任何 @Cacheable/@CacheEvict 的 cacheNames 都必须同步登记到此处。
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("projectPermissions", "rolePermissions", "superAdminFlag");
    }
}
