package com.seven7m.kanban.service.issue.attachment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.upload.AttachmentFileStore;
import com.seven7m.kanban.common.upload.ImageFileUtil;
import com.seven7m.kanban.common.upload.UploadProperties;
import com.seven7m.kanban.dto.issue.AttachmentVO;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.upload.UploadFile;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.upload.UploadFileMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.support.ProjectWriteGuard;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private final IssueAttachmentMapper attachmentMapper;
    private final SysUserMapper userMapper;
    private final WorkItemRouter workItemRouter;
    private final UploadProperties uploadProperties;
    private final UploadFileMapper uploadFileMapper;
    private final AttachmentFileStore attachmentFileStore;
    private final PermissionService permissionService;
    private final ProjectWriteGuard projectWriteGuard;

    /** 类别名（TASK/REQUIREMENT/BUG）；工作项不存在时由 router 统一抛 404 */
    private String categoryOf(UUID issueId) {
        return workItemRouter.categoryNameOrThrow(issueId);
    }

    public List<AttachmentVO> list(UUID issueId) {
        List<IssueAttachment> attachments = attachmentMapper.selectList(Wrappers.<IssueAttachment>lambdaQuery()
                .eq(IssueAttachment::getIssueId, issueId)
                .orderByDesc(IssueAttachment::getCreatedAt));
        // 上传人一次性批量查出，避免逐行查用户表（N+1）
        Set<UUID> uploaderIds = attachments.stream()
                .map(IssueAttachment::getUploadedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = uploaderIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(uploaderIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return attachments.stream().map(a -> toVO(a, userMap)).collect(Collectors.toList());
    }

    /**
     * 真实上传附件：校验图片类型与内容 → 落盘到 uploads 目录 → 写 attachment 记录（含 url）。
     *
     * <p>拆成 5 个步骤各自成方法：原来 89 行里混着配额校验、类型校验、落盘、入库、
     * 两种失败回滚路径，改任何一步都要通读整段。
     */
    @Transactional
    public AttachmentVO upload(UUID issueId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "上传文件不能为空");
        }
        // 归档项目只读：传附件同样属于修改工作项
        projectWriteGuard.requireWritable(workItemRouter.getProjectId(issueId));
        requireQuotaAvailable(issueId);
        if (file.getSize() > ImageFileUtil.maxFileSize()) {
            throw new ServiceException(400, "图片大小不能超过 5MB");
        }
        String ext = ImageFileUtil.resolveExtension(file.getOriginalFilename());
        byte[] data = readBytes(file);
        if (!ImageFileUtil.matchesMagicBytes(data, ext)) {
            throw new ServiceException(400, "文件内容与扩展名不符，已拒绝上传");
        }

        String filename = storeFile(data, ext);
        try {
            IssueAttachment a = saveAttachment(issueId, file, filename);
            // 同时登记 upload_file：UploadsAuthInterceptor 对查不到附件记录的文件只看登录态，
            // 登记上 projectId 之后即便是附件记录被删、文件残留，也仍然要求该项目的 issue:view 权限。
            registerUploadFile(filename, a.getUploadedBy(), a.getIssueId());
            registerRollbackCleanup(filename);
            return toVO(a);
        } catch (RuntimeException e) {
            // 入库失败：立即清理已落盘文件，避免产生孤儿文件
            deleteFileQuietly(filename);
            throw e;
        }
    }

    /** 配额前置于读取/落盘：超额请求不必白做一次完整文件 IO */
    private void requireQuotaAvailable(UUID issueId) {
        int maxPerIssue = uploadProperties.maxAttachmentsPerIssue();
        if (maxPerIssue <= 0) {
            return;
        }
        Long exist = attachmentMapper.selectCount(Wrappers.<IssueAttachment>lambdaQuery()
                .eq(IssueAttachment::getIssueId, issueId));
        if (exist != null && exist >= maxPerIssue) {
            throw new ServiceException(400, "单个工作项附件数量不能超过 " + maxPerIssue + " 个");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new ServiceException(400, "读取上传文件失败");
        }
    }

    /** 落盘并返回文件名（仅文件名，对外 URL 由 {@link #saveAttachment} 拼） */
    private String storeFile(byte[] data, String ext) {
        Path dir = uploadProperties.root();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ServiceException(500, "创建上传目录失败");
        }
        String filename = UUID.randomUUID() + "." + ext;
        try {
            Files.write(dir.resolve(filename), data);
        } catch (Exception e) {
            throw new ServiceException(500, "保存附件文件失败");
        }
        return filename;
    }

    private IssueAttachment saveAttachment(UUID issueId, MultipartFile file, String filename) {
        IssueAttachment a = new IssueAttachment();
        a.setId(UUID.randomUUID());
        a.setIssueId(issueId);
        // 显式落类别，避免取列默认值 TASK 导致需求/缺陷的附件统计与清理失效
        a.setCategory(categoryOf(issueId));
        a.setFileName(file.getOriginalFilename());
        a.setStorageKey(UploadProperties.STORAGE_KEY_PREFIX + filename);
        a.setContentType(file.getContentType());
        a.setFileSize(file.getSize());
        // 由配置推导访问路径，避免把 context-path（/api）写死：改部署前缀时历史数据与新数据都还能取到
        a.setUrl(uploadProperties.publicUrl(filename));
        a.setUploadedBy(UserContext.getUserId());
        a.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(a);
        return a;
    }

    /** 清理失败不影响主流程，残留文件交由运维定期清理 */
    private void deleteFileQuietly(String filename) {
        try {
            Files.deleteIfExists(uploadProperties.root().resolve(filename));
        } catch (Exception e) {
            // 见方法注释：清理是尽力而为；但要留痕，否则孤儿文件无从追查
            log.warn("附件文件清理失败（残留文件需运维清理）: file={}, reason={}", filename, e.toString());
        }
    }

    /**
     * 事务最终回滚时（如调用方后续操作失败）也要清理：
     * 文件在事务提交前已落盘，仅靠 insert 失败的 catch 覆盖不到"insert 成功但整体回滚"的场景。
     */
    private void registerRollbackCleanup(String filename) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // 仅确认回滚时才清理：STATUS_UNKNOWN 表示完成状态无法判定，
                // 若此时事务实际已提交却删了文件，会留下"有记录无文件"的坏附件，比孤儿文件更糟
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    deleteFileQuietly(filename);
                }
            }
        });
    }

    /**
     * 兼容旧调用：仅登记附件元信息（无真实文件），新客户端请走 upload。
     */
    @Transactional
    public AttachmentVO add(UUID issueId, String fileName, String contentType, Long fileSize) {
        projectWriteGuard.requireWritable(workItemRouter.getProjectId(issueId));
        IssueAttachment a = new IssueAttachment();
        a.setId(UUID.randomUUID());
        a.setIssueId(issueId);
        // 显式落类别，避免取列默认值 TASK 导致需求/缺陷的附件统计与清理失效
        a.setCategory(categoryOf(issueId));
        a.setFileName(fileName);
        a.setStorageKey("local/" + a.getId() + "/" + fileName);
        a.setContentType(contentType);
        a.setFileSize(fileSize);
        a.setUploadedBy(UserContext.getUserId());
        a.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(a);
        return toVO(a);
    }

    @Transactional
    public void delete(UUID issueId, UUID attachmentId) {
        IssueAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || !a.getIssueId().equals(issueId)) {
            return;
        }
        // 只能删自己上传的附件；系统管理员可代为清理（此前任何有编辑权限的人都能删别人的附件，且无审计）
        UUID currentUser = UserContext.getUserId();
        boolean isUploader = a.getUploadedBy() != null && a.getUploadedBy().equals(currentUser);
        if (!isUploader && !permissionService.isSuperAdmin(currentUser)) {
            throw new ServiceException(403, "只能删除自己上传的附件");
        }
        attachmentMapper.deleteById(attachmentId);
        // 同步清理磁盘文件（统一走 AttachmentFileStore：仅清理 uploads 目录内、防路径穿越）
        attachmentFileStore.deleteQuietly(a.getStorageKey());
    }

    /** 附件文件登记到 upload_file，保证文件访问鉴权能解析到所属项目 */
    private void registerUploadFile(String filename, UUID uploaderId, UUID issueId) {
        UUID projectId = workItemRouter.getProjectId(issueId);
        UploadFile record = new UploadFile();
        record.setId(UUID.randomUUID());
        record.setFilename(filename);
        record.setUploaderId(uploaderId);
        record.setProjectId(projectId);
        record.setCreatedAt(LocalDateTime.now());
        uploadFileMapper.insert(record);
    }

    /** 单条场景（upload/add）：只查一次上传人 */
    private AttachmentVO toVO(IssueAttachment a) {
        SysUser u = a.getUploadedBy() == null ? null : userMapper.selectById(a.getUploadedBy());
        return toVO(a, u == null ? Map.of() : Map.of(u.getId(), u));
    }

    /** 列表场景：上传人由调用方批量查好传入 */
    private AttachmentVO toVO(IssueAttachment a, Map<UUID, SysUser> userMap) {
        AttachmentVO vo = new AttachmentVO();
        BeanUtils.copyProperties(a, vo);
        SysUser u = a.getUploadedBy() == null ? null : userMap.get(a.getUploadedBy());
        vo.setUploaderName(u == null ? "未知" : u.getDisplayName());
        return vo;
    }
}
