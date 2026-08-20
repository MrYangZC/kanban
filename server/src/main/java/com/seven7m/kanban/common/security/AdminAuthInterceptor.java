package com.seven7m.kanban.common.security;

import com.seven7m.kanban.mapper.rbac.UserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 系统管理员权限拦截器：保护标注 {@link RequireAdmin} 的接口。
 * 判定：当前用户在任一项目的 project_member 中拥有 OWNER / ADMIN 角色即放行。
 * 依赖 UserContextInterceptor 先行填充操作人。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final UserRoleMapper userRoleMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm) || hm.getMethodAnnotation(RequireAdmin.class) == null) {
            return true;
        }
        UUID userId = UserContext.getUserId();
        int adminProjects = userRoleMapper.countAdminProjects(userId);
        if (adminProjects <= 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":403,\"msg\":\"无权限：需要系统管理员角色\",\"data\":null}");
            } catch (java.io.IOException ignored) {
                // 忽略写出异常，响应状态码已设置
            }
            return false;
        }
        return true;
    }
}
