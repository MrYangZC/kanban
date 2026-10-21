package com.seven7m.kanban.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.upload.UploadFile;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.upload.UploadFileMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 附件文件访问鉴权：/uploads/** 下已登记为工作项附件的文件，
 * 要求当前用户对附件所属项目具备 issue:view 权限，防止凭登录态横向越权下载。
 * 未登记的文件（如评论内嵌图片，无项目归属）仅要求登录态（由 UserContextInterceptor 保证）。
 */
@Component
@RequiredArgsConstructor
public class UploadsAuthInterceptor implements HandlerInterceptor {

    private static final String PATH_PREFIX = "/uploads/";

    private final IssueAttachmentMapper attachmentMapper;
    private final UploadFileMapper uploadFileMapper;
    private final PermissionService permissionService;
    private final IssueProjectCache issueProjectCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String filename = extractFilename(request);
        if (filename == null) {
            // 非单文件请求（目录、多级路径）：交由静态资源处理器处理（其自带路径规范化与 404）
            return true;
        }
        IssueAttachment attachment = attachmentMapper.selectOne(Wrappers.<IssueAttachment>lambdaQuery()
                .eq(IssueAttachment::getStorageKey, "uploads/" + filename)
                .last("LIMIT 1"));
        if (attachment == null || attachment.getIssueId() == null) {
            // 通用图片上传（评论/描述内嵌图、头像）：已登记项目归属的按项目成员校验，
            // 未登记或全站共享资源（头像）只要求登录态
            UploadFile upload = uploadFileMapper.selectOne(Wrappers.<UploadFile>lambdaQuery()
                    .eq(UploadFile::getFilename, filename).last("LIMIT 1"));
            if (upload != null && upload.getProjectId() != null
                    && !permissionService.hasPermission(UserContext.getUserId(), upload.getProjectId(), Permission.ISSUE_VIEW)) {
                return reject(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问该文件");
            }
            return true;
        }
        UUID projectId = issueProjectCache.getProjectId(attachment.getIssueId());
        if (projectId == null) {
            // 附件所属工作项已不存在：拒绝访问
            return reject(response, HttpServletResponse.SC_NOT_FOUND, "文件不存在");
        }
        if (!permissionService.hasPermission(UserContext.getUserId(), projectId, Permission.ISSUE_VIEW)) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问该附件");
        }
        return true;
    }

    /** 从请求路径提取 uploads 目录下的单级文件名；无法识别时返回 null */
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

    private boolean reject(HttpServletResponse response, int status, String msg) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\",\"data\":null}");
        } catch (Exception ignored) {
            // 响应状态码已设置
        }
        return false;
    }
}