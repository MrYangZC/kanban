package com.seven7m.kanban.common.security;

import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 系统管理员权限拦截器：保护标注 {@link RequireAdmin} 的接口。
 * 判定：sys_user.is_admin = true（系统级管理员标识，与项目级角色无关）。
 * 依赖 UserContextInterceptor 先行填充操作人。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final SysUserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        // 注解可能标注在方法或类上，两者都要识别：只查方法级会让类级标注静默失效（接口裸奔）
        RequireAdmin annotation = hm.getMethodAnnotation(RequireAdmin.class);
        if (annotation == null) {
            annotation = hm.getBeanType().getAnnotation(RequireAdmin.class);
        }
        if (annotation == null) {
            return true;
        }
        UUID userId = UserContext.getUserId();
        if (userId.equals(UserContext.DEFAULT_ADMIN_ID)) {
            return true;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getAdmin())) {
            // 审计线索：非管理员访问后台接口需要留痕
            log.warn("管理员权限校验失败 userId={} {} {}", userId,
                    request.getMethod(), request.getRequestURI());
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
