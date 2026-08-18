package com.seven7m.kanban.issue.attachment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.issue.dto.AttachmentVO;
import com.seven7m.kanban.user.entity.SysUser;
import com.seven7m.kanban.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private final IssueAttachmentMapper attachmentMapper;
    private final SysUserMapper userMapper;

    public List<AttachmentVO> list(UUID issueId) {
        return attachmentMapper.selectList(Wrappers.<IssueAttachment>lambdaQuery()
                        .eq(IssueAttachment::getIssueId, issueId)
                        .orderByDesc(IssueAttachment::getCreatedAt))
                .stream().map(a -> {
                    AttachmentVO vo = new AttachmentVO();
                    BeanUtils.copyProperties(a, vo);
                    SysUser u = userMapper.selectById(a.getUploadedBy());
                    vo.setUploaderName(u == null ? "未知" : u.getDisplayName());
                    return vo;
                }).collect(Collectors.toList());
    }

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

        AttachmentVO vo = new AttachmentVO();
        BeanUtils.copyProperties(a, vo);
        SysUser u = userMapper.selectById(a.getUploadedBy());
        vo.setUploaderName(u == null ? "未知" : u.getDisplayName());
        return vo;
    }

    @Transactional
    public void delete(UUID issueId, UUID attachmentId) {
        IssueAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || !a.getIssueId().equals(issueId)) {
            return;
        }
        attachmentMapper.deleteById(attachmentId);
    }
}
