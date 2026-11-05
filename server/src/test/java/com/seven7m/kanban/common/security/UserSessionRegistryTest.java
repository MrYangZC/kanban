package com.seven7m.kanban.common.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话登记表：账号被禁用/删除后必须能"踢下线"，这是"禁用不生效"的修复核心。
 */
class UserSessionRegistryTest {

    private final UserSessionRegistry registry = new UserSessionRegistry();

    private static HttpSession session(String id) {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    @Test
    void invalidateAll_kicksEverySessionOfThatUser() {
        UUID userId = UUID.randomUUID();
        HttpSession first = session("s1");
        HttpSession second = session("s2");
        registry.register(first, userId);
        registry.register(second, userId);

        assertEquals(2, registry.invalidateAll(userId));
        verify(first).invalidate();
        verify(second).invalidate();
    }

    @Test
    void invalidateAll_doesNotTouchOtherUsers() {
        UUID target = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        HttpSession targetSession = session("target");
        HttpSession otherSession = session("other");
        registry.register(targetSession, target);
        registry.register(otherSession, other);

        assertEquals(1, registry.invalidateAll(target));
        verify(otherSession, never()).invalidate();
    }

    @Test
    void invalidateOthers_keepsCurrentSessionAlive() {
        UUID userId = UUID.randomUUID();
        HttpSession current = session("current");
        HttpSession stale = session("stale");
        registry.register(current, userId);
        registry.register(stale, userId);

        assertEquals(1, registry.invalidateOthers(userId, "current"));
        verify(current, never()).invalidate();
        verify(stale).invalidate();
    }

    @Test
    void sessionDestroyed_unregistersSoUserCannotBeKickedTwice() {
        UUID userId = UUID.randomUUID();
        HttpSession session = session("s1");
        registry.register(session, userId);

        registry.sessionDestroyed(new HttpSessionEvent(session));

        assertEquals(0, registry.invalidateAll(userId));
        verify(session, never()).invalidate();
    }
}
