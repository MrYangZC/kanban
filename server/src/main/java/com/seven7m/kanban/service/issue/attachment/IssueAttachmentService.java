package com.seven7m.kanban.service.issue.attachment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.upload.ImageFileUtil;
import com.seven7m.kanban.common.upload.UploadProperties;
import com.seven7m.kanban.dto.issue.AttachmentVO;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
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

@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private final IssueAttachmentMapper attachmentMapper;
    private final SysUserMapper userMapper;
    private final WorkItemRouter workItemRouter;
    private final UploadProperties uploadProperties;

    /** 服务 context-path（默认 /api），用于拼附件的对外访问地址 */
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /** 根据工作项 id 判定所属类别（TASK/REQUIREMENT/BUG），不存在抛 404 */
    private String categoryOf(UUID issueId) {
        ItemCategory c = workItemRouter.categoryOf(issueId);
        if (c == null) throw new ServiceException(404, "工作项不存在");
        return c.name();
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
     */
    @Transactional
    public AttachmentVO upload(UUID issueId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "上传文件不能为空");
        }
        // 配额前置于读取/落盘：超额请求不必白做一次完整文件 IO
        int maxPerIssue = uploadProperties.maxAttachmentsPerIssue();
        if (maxPerIssue > 0) {
            Long exist = attachmentMapper.selectCount(Wrappers.<IssueAttachment>lambdaQuery()
                    .eq(IssueAttachment::getIssueId, issueId));
            if (exist != null && exist >= maxPerIssue) {
                throw new ServiceException(400, "单个工作项附件数量不能超过 " + maxPerIssue + " 个");
            }
        }
        if (file.getSize() > ImageFileUtil.maxFileSize()) {
            throw new ServiceException(400, "图片大小不能超过 5MB");
        }
        String ext = ImageFileUtil.resolveExtension(file.getOriginalFilename());
        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception e) {
            throw new ServiceException(400, "读取上传文件失败");
        }
        if (!ImageFileUtil.matchesMagicBytes(data, ext)) {
            throw new ServiceException(400, "文件内容与扩展名不符，已拒绝上传");
        }

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
        a.setUrl(contextPath + "/uploads/" + filename);
        a.setUploadedBy(UserContext.getUserId());
        a.setCreatedAt(LocalDateTime.now());
        try {
            attachmentMapper.insert(a);
        } catch (Exception e) {
            // insert 本身失败：立即清理已落盘文件，避免产生孤儿文件
            try {
                Files.deleteIfExists(uploadProperties.root().resolve(filename));
            } catch (Exception ignored) {
                // 清理失败不影响主流程，残留文件交由运维定期清理
            }
            throw e;
        }
        // 事务最终回滚时（如调用方后续操作失败）也要清理：
        // 文件在事务提交前已落盘，仅靠上面的 catch 覆盖不到"insert 成功但整体回滚"的场景。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    // 仅确认回滚时才清理：STATUS_UNKNOWN 表示完成状态无法判定，
                    // 若此时事务实际已提交却删了文件，会留下"有记录无文件"的坏附件，比孤儿文件更糟
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        try {
                            Files.deleteIfExists(uploadProperties.root().resolve(filename));
                        } catch (Exception ignored) {
                            // 清理失败不影响主流程，残留文件交由运维定期清理
                        }
                    }
                }
            });
        }
        return toVO(a);
    }

    /**
     * 兼容旧调用：仅登记附件元信息（无真实文件），新客户端请走 upload。
     */
    @Transactional
    public AttachmentVO add(UUID issueId, String fileName, String contentType, Long fileSize) {
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
        attachmentMapper.deleteById(attachmentId);
        // 同步清理磁盘文件（仅清理本地 uploads 目录内的文件，避免误删）
        if (a.getStorageKey() != null && a.getStorageKey().startsWith(UploadProperties.STORAGE_KEY_PREFIX)) {
            try {
                Path file = uploadProperties.root().resolve(a.getStorageKey().substring(UploadProperties.STORAGE_KEY_PREFIX.length())).normalize();
                if (file.startsWith(uploadProperties.root().normalize()) && Files.exists(file)) {
                    Files.deleteIfExists(file);
                }
            } catch (Exception ignored) {
                // 文件清理失败不影响记录删除，交由运维定期清理孤儿文件
            }
        }
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
