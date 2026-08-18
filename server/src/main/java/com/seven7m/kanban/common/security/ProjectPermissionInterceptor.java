package com.seven7m.kanban.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.annotation.ProjectRole;
import com.seven7m.kanban.issue.service.IssueService;
import com.seven7m.kanban.project.service.ProjectMemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

/**
 * 项目级角色鉴权拦截器。在 UserContextInterceptor 之后执行，从 @ProjectRole 注解读取角色要求，
 * 解析 projectId（路径变量或请求体 JSON），查询当前用户在项目中的角色并校验。
 * 无注解的方法直接放行；未登录（回退默认管理员）放行以兼容现有桩逻辑。
 */
@Component
@RequiredArgsConstructor
public class ProjectPermissionInterceptor implements HandlerInterceptor {

    private final ProjectMemberService projectMemberService;
    private final IssueService issueService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        ProjectRole annotation = AnnotationUtils.findAnnotation(hm.getMethod(), ProjectRole.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(hm.getBeanType(), ProjectRole.class);
        }
        if (annotation == null) {
            return true;
        }

        UUID projectId = resolveProjectId(request);
        if (projectId == null) {
            // 无法解析项目，交由 Controller 自身校验（如创建项目无需项目级角色）
            return true;
        }

        String role = projectMemberService.getRole(projectId, UserContext.getUserId());
        String[] required = annotation.required();
        boolean allowed = role != null;
        if (allowed) {
            allowed = false;
            for (String r : required) {
                if (r.equals(role)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) {
            ProjectRoleContext.set(role);
            throw new ServiceException(403, "无权限执行该操作，需要角色：" + String.join("/", required));
        }
        ProjectRoleContext.set(role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ProjectRoleContext.clear();
    }

    @SuppressWarnings("unchecked")
    private UUID resolveProjectId(HttpServletRequest request) {
        // 1. URI 模板变量中的 projectId / id（/v1/projects/{projectId}/... 或 /v1/issues/{id}）
        Object tmpl = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (tmpl instanceof Map<?, ?> vars) {
            UUID pid = asUuid((Map<String, Object>) vars, "projectId");
            if (pid != null) {
                return pid;
            }
            UUID issueId = asUuid((Map<String, Object>) vars, "id");
            if (issueId != null) {
                return issueService.getProjectId(issueId);
            }
        }
        // 2. 从请求体 JSON 解析（如 POST /v1/issues 的 projectId）。
        //    body 由 CachingRequestBodyFilter 预读并包装为 CachedBodyRequest（可重复读）。
        if ("POST".equalsIgnoreCase(request.getMethod()) && request instanceof CachedBodyRequest cached) {
            try {
                byte[] buf = cached.getBody();
                if (buf.length > 0) {
                    JsonNode node = objectMapper.readTree(buf);
                    JsonNode pid = node.get("projectId");
                    if (pid != null && !pid.isNull()) {
                        return UUID.fromString(pid.asText());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private UUID asUuid(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof UUID u) {
            return u;
        }
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
