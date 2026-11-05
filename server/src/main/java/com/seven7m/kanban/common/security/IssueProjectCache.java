package com.seven7m.kanban.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issue -> Project 解析的轻量缓存。
 * 独立于 ProjectMemberService，避免循环依赖（PermissionService -> ProjectMemberService）。
 *
 * 正确性约定：工作项被删除（或未来支持跨项目移动）后必须调用 {@link #invalidate}，
 * 否则权限拦截器会拿到陈旧的 projectId 放行/拒绝错误的项目。
 */
@Component
@RequiredArgsConstructor
public class IssueProjectCache {

    /** 容量上限：超限整体清空（进程内缓存的简单兜底，防止无界增长挤占内存） */
    private static final int MAX_SIZE = 100_000;

    /** 条目有效期：归属关系极少变化，长 TTL 足够支撑权限拦截的高频只读场景 */
    private static final long TTL_MS = 30 * 60 * 1000L;

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();

    /** 只依赖端口，不直接依赖 service.workitem（公共层不反向引用业务层） */
    private final IssueLocationPort issueLocationPort;

    private record Entry(UUID projectId, long expireAt) {
        boolean alive() {
            return System.currentTimeMillis() < expireAt;
        }
    }

    public UUID getProjectId(UUID issueId) {
        Entry entry = CACHE.get(issueId);
        if (entry != null && entry.alive()) {
            return entry.projectId();
        }
        UUID projectId = issueLocationPort.projectIdOf(issueId);
        if (CACHE.size() >= MAX_SIZE) {
            CACHE.clear();
        }
        // 连未命中（projectId=null）也缓存，避免不存在的 id 反复穿透查库
        CACHE.put(issueId, new Entry(projectId, System.currentTimeMillis() + TTL_MS));
        return projectId;
    }

    /** 工作项删除后调用，避免权限解析拿到陈旧 projectId */
    public static void invalidate(UUID issueId) {
        CACHE.remove(issueId);
    }
}
