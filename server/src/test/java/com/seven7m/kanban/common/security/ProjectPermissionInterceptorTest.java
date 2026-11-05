package com.seven7m.kanban.common.security;

import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 项目级权限拦截器测试。
 *
 * <p>这是全站最关键的鉴权闸门：判定错了会直接越权或误伤。此前没有任何测试覆盖，
 * 只靠"点一下试试"。这里固定住四条行为：无注解放行、权限不足 403、缺少项目标识 400、
 * 非法 projectId 交给 Spring 报 400（不能用 403 掩盖参数错误）。
 */
class ProjectPermissionInterceptorTest {

    private static final UUID PROJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PermissionService permissionService = mock(PermissionService.class);
    private final IssueProjectCache issueProjectCache = mock(IssueProjectCache.class);
    private final ProjectPermissionInterceptor interceptor =
            new ProjectPermissionInterceptor(permissionService, issueProjectCache);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------------- 测试用 Handler ----------------

    static class SecuredHandler {
        @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
        public void view() {
        }

        @ProjectRoleAnnotation({Permission.PROJECT_DELETE})
        public void delete() {
        }

        public void open() {
        }
    }

    @ProjectRoleAnnotation(Permission.PROJECT_SETTING)
    static class ClassLevelSecuredHandler {
        public void update() {
        }
    }

    private static HandlerMethod handler(Object bean, String methodName) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
    }

    private MockHttpServletRequest requestWithPathVars(Map<String, String> pathVars) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/projects/" + PROJECT_ID + "/issues");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVars);
        return request;
    }

    @Test
    @DisplayName("没有权限注解：直接放行，不查权限")
    void allowsHandlerWithoutAnnotation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response,
                handler(new SecuredHandler(), "open"));

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("路径里的 projectId + 有权限：放行")
    void allowsWhenPermissionGranted() throws Exception {
        UserContext.set(USER_ID, "张三");
        when(permissionService.hasPermission(USER_ID, PROJECT_ID, Permission.ISSUE_VIEW)).thenReturn(true);

        boolean allowed = interceptor.preHandle(requestWithPathVars(Map.of("projectId", PROJECT_ID.toString())),
                new MockHttpServletResponse(), handler(new SecuredHandler(), "view"));

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("权限不足：403 且响应体带 code=403")
    void rejectsWhenPermissionMissing() throws Exception {
        UserContext.set(USER_ID, "张三");
        when(permissionService.hasPermission(any(), any(), anyString())).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(requestWithPathVars(Map.of("projectId", PROJECT_ID.toString())),
                response, handler(new SecuredHandler(), "delete"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("\"code\":403");
    }

    @Test
    @DisplayName("请求里完全没有项目标识：400（参数缺失，不是无权限）")
    void rejectsMissingProjectIdentifier() throws Exception {
        UserContext.set(USER_ID, "张三");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/issues/filter");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, handler(new SecuredHandler(), "view"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("projectId 不是合法 UUID：放行交给 Spring 报参数错误，不用 403 掩盖")
    void passesThroughMalformedId() throws Exception {
        UserContext.set(USER_ID, "张三");

        boolean allowed = interceptor.preHandle(requestWithPathVars(Map.of("projectId", "not-a-uuid")),
                new MockHttpServletResponse(), handler(new SecuredHandler(), "view"));

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("类级注解同样生效（只查方法级会让整类接口裸奔）")
    void honoursClassLevelAnnotation() throws Exception {
        UserContext.set(USER_ID, "张三");
        when(permissionService.hasPermission(any(), any(), anyString())).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(requestWithPathVars(Map.of("projectId", PROJECT_ID.toString())),
                response, handler(new ClassLevelSecuredHandler(), "update"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }
}
