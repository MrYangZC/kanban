package com.seven7m.kanban.common.security;

import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理员鉴权拦截器测试。
 * 重点覆盖注解的两种标注位置：只识别方法级会让类级标注的接口静默放行。
 */
class AdminAuthInterceptorTest {

    private static final UUID NORMAL_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor(userMapper);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------------- 测试用 Handler ----------------

    static class MethodLevelHandler {
        @RequireAdmin
        public void secured() {
        }

        public void open() {
        }
    }

    @RequireAdmin
    static class TypeLevelHandler {
        public void securedByClass() {
        }
    }

    static class PlainHandler {
        public void open() {
        }
    }

    private static HandlerMethod handler(Object bean, String methodName) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
    }

    private static SysUser user(boolean isAdmin) {
        SysUser u = new SysUser();
        u.setId(NORMAL_USER_ID);
        u.setIsAdmin(isAdmin);
        return u;
    }

    private boolean preHandle(Object bean, String methodName, MockHttpServletResponse response) throws Exception {
        return interceptor.preHandle(new MockHttpServletRequest(), response, handler(bean, methodName));
    }

    // ---------------- 用例 ----------------

    @Test
    @DisplayName("方法级 @RequireAdmin：非系统管理员被拒 403")
    void methodLevelAnnotationRejectsNonAdmin() throws Exception {
        UserContext.set(NORMAL_USER_ID, "tester");
        when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(user(false));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(preHandle(new MethodLevelHandler(), "secured", response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("类级 @RequireAdmin：对类内端点同样生效（防新增方法漏保护）")
    void typeLevelAnnotationRejectsNonAdmin() throws Exception {
        UserContext.set(NORMAL_USER_ID, "tester");
        when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(user(false));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(preHandle(new TypeLevelHandler(), "securedByClass", response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("未标注注解的接口不因本拦截器被拦（即便非管理员）")
    void unannotatedEndpointIsNotAffected() throws Exception {
        UserContext.set(NORMAL_USER_ID, "tester");
        when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(user(false));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(preHandle(new MethodLevelHandler(), "open", response)).isTrue();
        assertThat(preHandle(new PlainHandler(), "open", response)).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("系统管理员（sys_user.is_admin=true）可通过被保护接口")
    void systemAdminIsAllowed() throws Exception {
        UserContext.set(NORMAL_USER_ID, "tester");
        when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(user(true));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(preHandle(new MethodLevelHandler(), "secured", response)).isTrue();
        assertThat(preHandle(new TypeLevelHandler(), "securedByClass", response)).isTrue();
    }

    @Test
    @DisplayName("用户不存在时不放行")
    void unknownUserIsRejected() throws Exception {
        UserContext.set(NORMAL_USER_ID, "ghost");
        when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(preHandle(new MethodLevelHandler(), "secured", response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("非 HandlerMethod（静态资源等）直接放行")
    void nonHandlerMethodIsPassedThrough() {
        UserContext.set(NORMAL_USER_ID, "tester");
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }
}
