package com.seven7m.kanban.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.upload.UploadFile;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.upload.UploadFileMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 附件文件访问鉴权（替代原来的 {@code UploadsAuthInterceptor}）。
 *
 * <p>规则与旧实现一致：
 * <ul>
 *   <li>登记为工作项附件的文件 → 需要该工作项所属项目的 {@code issue:view}；</li>
 *   <li>登记了项目归属的通用图片 → 需要该项目 {@code issue:view}；</li>
 *   <li>未登记或全站共享资源（头像） → 只要登录即可。</li>
 * </ul>
 * 换成 Spring Security 的 {@link AuthorizationManager} 后，判定结果直接接入统一的
 * 401/403 出口，不再有一套并行的手写响应。
 */
@Component
@RequiredArgsConstructor
public class UploadsAccessManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String PATH_PREFIX = "/uploads/";

    private final IssueAttachmentMapper attachmentMapper;
    private final UploadFileMapper uploadFileMapper;
    private final PermissionService permissionService;
    private final IssueProjectCache issueProjectCache;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                        RequestAuthorizationContext context) {
        UUID userId = UserContext.getUserIdOrNull();
        if (userId == null) {
            return new AuthorizationDecision(false);
        }
        HttpServletRequest request = context.getRequest();
        String filename = extractFilename(request);
        if (filename == null) {
            // 目录或多级路径：交给静态资源处理器（其自带规范化与 404）
            return new AuthorizationDecision(true);
        }
        IssueAttachment attachment = attachmentMapper.selectOne(Wrappers.<IssueAttachment>lambdaQuery()
                .eq(IssueAttachment::getStorageKey, "uploads/" + filename)
                .last("LIMIT 1"));
        if (attachment == null || attachment.getIssueId() == null) {
            UploadFile upload = uploadFileMapper.selectOne(Wrappers.<UploadFile>lambdaQuery()
                    .eq(UploadFile::getFilename, filename)
                    .last("LIMIT 1"));
            if (upload != null && upload.getProjectId() != null) {
                return new AuthorizationDecision(
                        permissionService.hasPermission(userId, upload.getProjectId(), Permission.ISSUE_VIEW));
            }
            return new AuthorizationDecision(true);
        }
        UUID projectId = issueProjectCache.getProjectId(attachment.getIssueId());
        if (projectId == null) {
            // 工作项已不存在：拒绝（文件成孤儿）
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(
                permissionService.hasPermission(userId, projectId, Permission.ISSUE_VIEW));
    }

    private String extractFilename(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;
        if (!path.startsWith(PATH_PREFIX)) {
            return null;
        }
        String filename = path.substring(PATH_PREFIX.length());
        if (filename.isEmpty() || filename.contains("/") || filename.contains("..")) {
            return null;
        }
        return filename;
    }
}
