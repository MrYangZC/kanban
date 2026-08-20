package com.seven7m.kanban.common.security;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.common.security.RequireAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 项目级权限拦截器：依据接口声明的“操作权限”（READ/WRITE/MANAGE）进行鉴权。
 * 角色 -> 操作权限的映射存于 role_permission 表，运行时由 PermissionService 动态判定，
 * 因此新增/调整角色无需改动本拦截器或注解。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectPermissionInterceptor implements HandlerInterceptor {

    private final PermissionService permissionService;
    private final IssueProjectCache issueProjectCache;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        ProjectRoleAnnotation ann = hm.getMethodAnnotation(ProjectRoleAnnotation.class);
        if (ann == null) {
            ann = hm.getBeanType().getAnnotation(ProjectRoleAnnotation.class);
        }
        if (ann == null) {
            // 需要登录态（UserContextInterceptor 已校验），但无项目级权限要求
            return true;
        }

        UUID userId = UserContext.getUserId();
        UUID projectId = resolveProjectId(request);
        if (projectId == null) {
            // 路径变量无 projectId 时，尝试从缓存的请求体(projectId 字段)解析，兼容 /issues/filter 等
            projectId = resolveProjectIdFromBody(request);
        }
        final UUID resolvedProjectId = projectId;
        if (resolvedProjectId == null) {
            // 无法解析出项目（请求体未被缓存等情况）：拒绝，避免越权放行
            writeForbidden(response, "无法解析项目上下文");
            return false;
        }

        Permission[] required = ann.value();
        boolean ok = java.util.Arrays.stream(required)
                .allMatch(p -> permissionService.hasPermission(userId, resolvedProjectId, p));
        if (!ok) {
            writeForbidden(response, "无权限执行该操作");
            return false;
        }
        return true;
    }

    /**
     * 从路径变量中解析 projectId。
     * 直接挂在项目下的资源使用 projectId；挂在 issue 下的资源使用 issueId，
     * 再通过 IssueMapper/缓存解析出所属 projectId。
     */
    private UUID resolveProjectId(HttpServletRequest request) {
        String projectId = request.getAttribute("projectId") != null
                ? request.getAttribute("projectId").toString() : null;
        if (projectId != null) {
            try {
                return UUID.fromString(projectId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String issueId = request.getAttribute("issueId") != null
                ? request.getAttribute("issueId").toString() : null;
        if (issueId != null) {
            try {
                UUID issueUUID = UUID.fromString(issueId);
                return issueProjectCache.getProjectId(issueUUID);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String id = request.getAttribute("id") != null
                ? request.getAttribute("id").toString() : null;
        if (id != null) {
            try {
                UUID idUUID = UUID.fromString(id);
                return issueProjectCache.getProjectId(idUUID);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    /**
     * 兜底：从缓存的请求体(projectId 字段)解析，兼容 POST /issues/filter 等 projectId 在 body 的接口。
     * 依赖 CachingRequestBodyFilter 预读并包装为 CachedBodyRequest。
     */
    private UUID resolveProjectIdFromBody(HttpServletRequest request) {
        if (!(request instanceof CachedBodyRequest cached)) {
            return null;
        }
        byte[] body = cached.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body);
            JsonNode pid = node.get("projectId");
            if (pid != null && !pid.isNull()) {
                return UUID.fromString(pid.asText());
            }
        } catch (Exception e) {
            log.debug("从请求体解析 projectId 失败", e);
        }
        return null;
    }

    private void writeForbidden(HttpServletResponse response, String msg) {
        try {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            String json = "{\"code\":403,\"msg\":" + escape(msg) + ",\"data\":null}";
            response.getWriter().write(json);
        } catch (Exception e) {
            log.warn("写回 403 失败", e);
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
