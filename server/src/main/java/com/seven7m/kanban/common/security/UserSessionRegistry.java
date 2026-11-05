package com.seven7m.kanban.common.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录会话登记表：记录「用户 → HttpSession」，用于在账号被禁用/删除/改密后**立即**踢掉旧会话。
 *
 * <p>背景：登录态完全由服务端 HttpSession 承载，而 {@link UserContextInterceptor} 只校验
 * 会话里有没有 uid。此前把一个账号 {@code enabled=false} 或直接删除，旧会话依然通行到
 * 会话超时（默认 30 分钟）或进程重启为止——"禁用"实际不生效。
 *
 * <p>实现刻意保持轻量：本项目没有引入 Spring Security，因此不能用它的 SessionRegistry。
 * 会话销毁（登出/超时）由 {@link #sessionDestroyed} 回调清理，避免 Map 无界增长。
 */
@Slf4j
@Component
public class UserSessionRegistry implements HttpSessionListener {

    /** 单实例跟踪的会话上限：超限先清理已失效会话，仍超限则整体丢弃（宁可漏踢，不可 OOM） */
    private static final int MAX_TRACKED_SESSIONS = 20_000;

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    private record Entry(UUID userId, HttpSession session) {
    }

    /** 登录成功后登记；同一会话重复登录时覆盖 */
    public void register(HttpSession session, UUID userId) {
        if (session == null || userId == null) {
            return;
        }
        if (sessions.size() >= MAX_TRACKED_SESSIONS) {
            pruneInvalidated();
            if (sessions.size() >= MAX_TRACKED_SESSIONS) {
                log.warn("会话登记表已达上限 {}，丢弃历史记录（旧会话将无法被主动踢出）", MAX_TRACKED_SESSIONS);
                sessions.clear();
            }
        }
        sessions.put(session.getId(), new Entry(userId, session));
    }

    /** 登出时移除；会话本身的销毁由 sessionDestroyed 兜底 */
    public void unregister(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    /**
     * 让某用户的全部会话立即失效。
     *
     * @return 被踢下线的会话数
     */
    public int invalidateAll(UUID userId) {
        return invalidate(userId, null);
    }

    /**
     * 让某用户除当前会话外的其余会话失效（改密场景：本人当前这次操作不应被自己踢掉）。
     */
    public int invalidateOthers(UUID userId, String keepSessionId) {
        return invalidate(userId, keepSessionId);
    }

    private int invalidate(UUID userId, String keepSessionId) {
        if (userId == null) {
            return 0;
        }
        List<String> handled = new ArrayList<>();
        for (Map.Entry<String, Entry> e : sessions.entrySet()) {
            Entry entry = e.getValue();
            if (!userId.equals(entry.userId()) || e.getKey().equals(keepSessionId)) {
                continue;
            }
            handled.add(e.getKey());
            try {
                entry.session().invalidate();
            } catch (IllegalStateException ignored) {
                // 会话已被容器回收（超时/登出），无需处理
            }
        }
        handled.forEach(sessions::remove);
        if (!handled.isEmpty()) {
            log.info("已强制下线用户 {} 的 {} 个会话", userId, handled.size());
        }
        return handled.size();
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        unregister(se.getSession().getId());
    }

    private void pruneInvalidated() {
        sessions.entrySet().removeIf(e -> {
            try {
                // 会话失效后访问任何方法都会抛 IllegalStateException
                e.getValue().session().getCreationTime();
                return false;
            } catch (IllegalStateException ex) {
                return true;
            }
        });
    }
}
