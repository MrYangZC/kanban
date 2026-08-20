package com.seven7m.kanban.service.issue.attachment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.upload.ImageFileUtil;
import com.seven7m.kanban.dto.issue.AttachmentVO;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private static final Path UPLOAD_DIR = Paths.get("./uploads");

    private final IssueAttachmentMapper attachmentMapper;
    private final SysUserMapper userMapper;

    public List<AttachmentVO> list(UUID issueId) {
        return attachmentMapper.selectList(Wrappers.<IssueAttachment>lambdaQuery()
                        .eq(IssueAttachment::getIssueId, issueId)
                        .orderByDesc(IssueAttachment::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 真实上传附件：校验图片类型与内容 → 落盘到 uploads 目录 → 写 attachment 记录（含 url）。
     */
    @Transactional
    public AttachmentVO upload(UUID issueId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "上传文件不能为空");
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

        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (Exception e) {
            throw new ServiceException(500, "创建上传目录失败");
        }
        String filename = UUID.randomUUID() + "." + ext;
        try {
            Files.write(UPLOAD_DIR.resolve(filename), data);
        } catch (Exception e) {
            throw new ServiceException(500, "保存附件文件失败");
        }

        IssueAttachment a = new IssueAttachment();
        a.setId(UUID.randomUUID());
        a.setIssueId(issueId);
        a.setFileName(file.getOriginalFilename());
        a.setStorageKey("uploads/" + filename);
        a.setContentType(file.getContentType());
        a.setFileSize(file.getSize());
        a.setUrl("/api/uploads/" + filename);
        a.setUploadedBy(UserContext.getUserId());
        a.setCreatedAt(LocalDateTime.now());
        try {
            attachmentMapper.insert(a);
        } catch (Exception e) {
            // 数据库写入失败时清理已落盘文件，避免产生孤儿文件
            try {
                Files.deleteIfExists(UPLOAD_DIR.resolve(filename));
            } catch (Exception ignored) {
            }
            throw e;
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
        if (a.getStorageKey() != null && a.getStorageKey().startsWith("uploads/")) {
            try {
                Path file = UPLOAD_DIR.resolve(a.getStorageKey().substring("uploads/".length())).normalize();
                if (file.startsWith(UPLOAD_DIR.normalize()) && Files.exists(file)) {
                    Files.deleteIfExists(file);
                }
            } catch (Exception ignored) {
                // 文件清理失败不影响记录删除，交由运维定期清理孤儿文件
            }
        }
    }

    private AttachmentVO toVO(IssueAttachment a) {
        AttachmentVO vo = new AttachmentVO();
        BeanUtils.copyProperties(a, vo);
        SysUser u = userMapper.selectById(a.getUploadedBy());
        vo.setUploaderName(u == null ? "未知" : u.getDisplayName());
        return vo;
    }
}
