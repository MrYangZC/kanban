package com.seven7m.kanban.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 操作人上下文拦截器：登录成功后由 UserController 把用户 id 写入 HttpSession("uid")，
 * 本拦截器在请求入口从 Session 还原真实用户；未登录时回退默认管理员（仅本地开发桩），
 * 结束时清理 ThreadLocal，避免线程池复用导致操作人串号。后续接入 JWT/SaToken 后改为从令牌还原。
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
        // 未登录：回退默认管理员，便于无登录态调试；权限校验仍按当前 userId 进行
        UserContext.set(UserContext.DEFAULT_ADMIN_ID, UserContext.DEFAULT_ADMIN_NAME);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
