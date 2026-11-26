package com.seven7m.kanban.common.security.method;

import com.seven7m.kanban.common.security.IssueProjectCache;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 供 {@code @PreAuthorize} 调用的项目级权限判定器。
 *
 * <p>为什么不是 {@code hasAuthority('issue:view')}：项目级权限的语义是
 * 「某用户在**某个项目**里有没有某权限」，而 authorities 是与会话绑定的全局集合。
 * 把所有项目的权限都写进 JWT，会有三个问题：token 随项目数膨胀、新建项目后要重新登录才生效、
 * 角色变更后 token 里仍是旧权限。因此项目级判定必须带上 projectId 现查：
 *
 * <pre>{@code @PreAuthorize("@projectAuth.has(#projectId, 'issue:view')")}</pre>
 *
 * <p>projectId 显式从方法参数取（路径变量或请求体字段），而不是靠切面去嗅探请求体——
 * 后者需要预读 body，既多一层 IO，也让"权限依据"变得隐晦。
 */
@Slf4j
@Component("projectAuth")
@RequiredArgsConstructor
public class ProjectAuth {

    private final PermissionService permissionService;
    private final IssueProjectCache issueProjectCache;

    /** 当前登录用户对指定项目是否具备该权限码；projectId 为空时直接拒绝（宁可 403 也不放过） */
    public boolean has(UUID projectId, String code) {
        UUID userId = UserContext.getUserIdOrNull();
        if (userId == null || projectId == null) {
            return false;
        }
        return permissionService.hasPermission(userId, projectId, code);
    }

    /** 以工作项 id 判定：先解析该工作项所属项目（附件、评论等挂在 issue 下的接口用这个） */
    public boolean hasIssue(UUID issueId, String code) {
        if (issueId == null) {
            return false;
        }
        UUID projectId = issueProjectCache.getProjectId(issueId);
        return projectId != null && has(projectId, code);
    }

    /**
     * 以路径参数 {@code id} 判定：同一个 {@code /{id}} 在不同控制器里含义不同——
     * {@code /projects/{id}} 的 id 是项目，{@code /issues/{id}} 的 id 是工作项。
     * 先按工作项解析，解析不到再当作项目 id，覆盖这两种约定。
     */
    public boolean hasId(UUID id, String code) {
        if (id == null) {
            return false;
        }
        UUID projectId = issueProjectCache.getProjectId(id);
        return has(projectId != null ? projectId : id, code);
    }
}
