package com.seven7m.kanban.common.security.method;

import com.seven7m.kanban.common.security.IssueProjectCache;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目级权限判定的守护测试。
 *
 * <p>三种入参约定（projectId / issueId / id）各自对应不同控制器风格，之前那套拦截器
 * 靠"猜路径变量名"实现，改名就会静默失效。这里把三条约定钉死。
 */
class ProjectAuthTest {

    private final PermissionService permissionService = mock(PermissionService.class);
    private final IssueProjectCache issueProjectCache = mock(IssueProjectCache.class);
    private final ProjectAuth auth = new ProjectAuth(permissionService, issueProjectCache);

    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("has：按项目判定，透传到 PermissionService")
    void hasDelegatesToPermissionService() {
        UserContext.set(userId, "alice");
        when(permissionService.hasPermission(userId, projectId, "issue:view")).thenReturn(true);

        assertThat(auth.has(projectId, "issue:view")).isTrue();
    }

    @Test
    @DisplayName("未登录时直接拒绝，且不查库")
    void anonymousIsRejectedWithoutQuery() {
        UserContext.clear();

        assertThat(auth.has(projectId, "issue:view")).isFalse();
        verify(permissionService, never()).hasPermission(any(), any(), any());
    }

    @Test
    @DisplayName("projectId 为空时拒绝（防止注解写错导致越权）")
    void nullProjectIdIsRejected() {
        UserContext.set(userId, "alice");

        assertThat(auth.has(null, "issue:view")).isFalse();
    }

    @Test
    @DisplayName("hasIssue：先由工作项反查项目，再判定")
    void hasIssueResolvesProjectFirst() {
        UserContext.set(userId, "alice");
        UUID issueId = UUID.randomUUID();
        when(issueProjectCache.getProjectId(issueId)).thenReturn(projectId);
        when(permissionService.hasPermission(userId, projectId, "issue:comment")).thenReturn(true);

        assertThat(auth.hasIssue(issueId, "issue:comment")).isTrue();
    }

    @Test
    @DisplayName("hasIssue：工作项不存在（项目解析不到）时拒绝")
    void hasIssueWithUnknownIssueIsRejected() {
        UserContext.set(userId, "alice");
        UUID issueId = UUID.randomUUID();
        when(issueProjectCache.getProjectId(issueId)).thenReturn(null);

        assertThat(auth.hasIssue(issueId, "issue:comment")).isFalse();
    }

    @Test
    @DisplayName("hasId：能按工作项解析就按工作项，解析不到才当作项目 id")
    void hasIdPrefersIssueThenProject() {
        UserContext.set(userId, "alice");
        UUID issueId = UUID.randomUUID();
        when(issueProjectCache.getProjectId(issueId)).thenReturn(projectId);
        when(permissionService.hasPermission(userId, projectId, "issue:edit")).thenReturn(true);

        assertThat(auth.hasId(issueId, "issue:edit")).isTrue();
        verify(permissionService, never()).hasPermission(eq(userId), eq(issueId), any());

        // 解析不到工作项时，把 id 当项目 id（ProjectController 的 {id} 就是项目）
        UUID pureProject = UUID.randomUUID();
        when(issueProjectCache.getProjectId(pureProject)).thenReturn(null);
        when(permissionService.hasPermission(userId, pureProject, "project:setting")).thenReturn(true);

        assertThat(auth.hasId(pureProject, "project:setting")).isTrue();
    }
}
