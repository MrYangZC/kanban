package com.seven7m.kanban.common.security;

import java.util.UUID;

/**
 * 工作项 → 所属项目的只读端口。
 *
 * <p>{@link IssueProjectCache} 属于公共安全层（权限拦截器在 preHandle 里用它解析 projectId），
 * 但它需要查工作项落在哪张表。此前直接依赖 `service.workitem.WorkItemRouter`，
 * 造成"公共层引用业务层"；现在改为面向本端口，实现放在 `WorkItemRouter`。
 */
public interface IssueLocationPort {

    /** 工作项所属项目 id；工作项不存在返回 null */
    UUID projectIdOf(UUID issueId);
}
