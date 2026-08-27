package com.seven7m.kanban.common.security;

import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issue -> Project 解析的轻量缓存。
 * 独立于 ProjectMemberService，避免循环依赖（PermissionService -> ProjectMemberService）。
 * 进程内缓存，TTL 仅靠容量上限与写覆盖，足够支撑权限拦截的高频只读场景。
 */
@Component
@RequiredArgsConstructor
public class IssueProjectCache {

    private static final Map<UUID, UUID> CACHE = new ConcurrentHashMap<>();

    private final WorkItemRouter workItemRouter;

    public UUID getProjectId(UUID issueId) {
        return CACHE.computeIfAbsent(issueId, workItemRouter::getProjectId);
    }

    public static void invalidate(UUID issueId) {
        CACHE.remove(issueId);
    }
}
