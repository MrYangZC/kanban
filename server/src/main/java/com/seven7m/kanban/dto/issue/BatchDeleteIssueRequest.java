package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 批量删除工作项请求。
 *
 * <p>带 {@code projectId} 不只是为了定位——权限拦截器
 * （{@code ProjectPermissionInterceptor}）在路径变量里没有 projectId 时，
 * 会从请求体的 {@code projectId} 字段解析出项目来做 {@code issue:delete} 校验；
 * 同时服务端据此拒绝删除其它项目下的工作项，避免越权。
 */
@Data
public class BatchDeleteIssueRequest {

    @NotNull(message = "projectId 不能为空")
    private UUID projectId;

    /** 一次最多 500 条：再多也应该走「删项目」，而不是一个请求拖垮事务 */
    @NotEmpty(message = "ids 不能为空")
    @Size(max = 500, message = "一次最多删除 500 条")
    private List<UUID> ids;
}
