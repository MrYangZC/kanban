package com.seven7m.kanban.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 操作人上下文拦截器：登录成功后由 UserController 把用户 id 写入 HttpSession("uid")，
 * 本拦截器在请求入口从 Session 还原真实用户；未登录（无有效会话）时返回 401，拒绝访问，
 * 结束时清理 ThreadLocal，避免线程池复用导致操作人串号。登录接口本身在 WebConfig 中放行。
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    public static final String SESSION_UID = "uid";
    public static final String SESSION_UNAME = "uname";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object uid = session.getAttribute(SESSION_UID);
            Object uname = session.getAttribute(SESSION_UNAME);
            if (uid instanceof UUID) {
                UserContext.set((UUID) uid, uname instanceof String ? (String) uname : UserContext.DEFAULT_ADMIN_NAME);
                return true;
            }
        }
        // 未登录：拒绝访问（HTTP 401）
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录\",\"data\":null}");
        } catch (java.io.IOException ignored) {
            // 忽略写出异常，响应状态码已设置
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
