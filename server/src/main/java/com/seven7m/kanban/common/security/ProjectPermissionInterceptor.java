package com.seven7m.kanban.common.security;

import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.common.util.Uuids;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
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
        @SuppressWarnings("unchecked")
        Map<String, String> pathVars =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        UUID projectId = resolveProjectId(pathVars);
        if (projectId == null) {
            // 路径变量无 projectId 时，尝试从缓存的请求体(projectId 字段)解析，兼容 /issues/filter 等
            projectId = resolveProjectIdFromBody(request);
        }
        final UUID resolvedProjectId = projectId;
        if (resolvedProjectId == null) {
            if (hasMalformedId(pathVars)) {
                // 路径变量存在但不是合法 UUID：放行交给 Spring 参数解析，返回「参数类型错误」的 400，
                // 不用 403 掩盖真正的参数问题
                return true;
            }
            // 请求里完全没有项目标识，属于参数缺失（400）而非无权限（403）
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "缺少参数：projectId");
            return false;
        }

        String[] required = ann.value();
        boolean ok = java.util.Arrays.stream(required)
                .allMatch(p -> permissionService.hasPermission(userId, resolvedProjectId, p));
        if (!ok) {
            // 审计线索：线上"某人越权访问"必须能查到，而不是只有一句 403
            log.warn("权限不足 userId={} projectId={} 需要={} {} {}",
                    userId, resolvedProjectId, java.util.Arrays.toString(required),
                    request.getMethod(), request.getRequestURI());
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "无权限执行该操作");
            return false;
        }
        return true;
    }

    /**
     * 从路径变量中解析 projectId。
     * 直接挂在项目下的资源使用 projectId；挂在 issue 下的资源使用 issueId，
     * 再通过 WorkItemRouter/缓存解析出所属 projectId。
     */
    private UUID resolveProjectId(Map<String, String> pathVars) {
        if (pathVars != null) {
            UUID projectUuid = Uuids.parseOrNull(pathVars.get("projectId"));
            if (projectUuid != null) {
                return projectUuid;
            }
            UUID issueUuid = Uuids.parseOrNull(pathVars.get("issueId"));
            if (issueUuid != null) {
                return issueProjectCache.getProjectId(issueUuid);
            }
            UUID idVal = Uuids.parseOrNull(pathVars.get("id"));
            if (idVal != null) {
                UUID pid = issueProjectCache.getProjectId(idVal);
                return pid != null ? pid : idVal;
            }
        }
        return null;
    }

    /** 路径变量中存在「不是合法 UUID」的标识时，交由 Spring 抛参数类型错误，而不是用权限错误掩盖 */
    private static boolean hasMalformedId(Map<String, String> pathVars) {
        if (pathVars == null) {
            return false;
        }
        for (String key : new String[]{"projectId", "issueId", "id"}) {
            String value = pathVars.get(key);
            if (value != null && !Uuids.isUuid(value)) {
                return true;
            }
        }
        return false;
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
                return Uuids.parseOrNull(pid.asText());
            }
        } catch (Exception e) {
            log.debug("从请求体解析 projectId 失败", e);
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String msg) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            String json = "{\"code\":" + status + ",\"msg\":" + escape(msg) + ",\"data\":null}";
            response.getWriter().write(json);
        } catch (Exception e) {
            log.warn("写回 {} 失败", status, e);
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
