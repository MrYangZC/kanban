package com.seven7m.kanban.issue.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.issue.entity.IssueAttachment;
import com.seven7m.kanban.issue.mapper.IssueAttachmentMapper;
import com.seven7m.kanban.user.entity.User;
import com.seven7m.kanban.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueAttachmentService {

    private final IssueAttachmentMapper attachmentMapper;
    private final UserMapper userMapper;

    public List<IssueAttachment> list(UUID issueId) {
        return attachmentMapper.selectList(Wrappers.<IssueAttachment>lambdaQuery()
                        .eq(IssueAttachment::getIssueId, issueId)
                        .orderByDesc(IssueAttachment::getCreatedAt))
                .stream().peek(a -> {
                    User u = userMapper.selectById(a.getUploadedBy());
                    if (u != null) a.setUrl(u.getName());
                }).collect(Collectors.toList());
    }

    public IssueAttachment add(UUID issueId, String fileName, String storageKey,
                               String contentType, Long fileSize, UUID uploadedBy) {
        IssueAttachment a = new IssueAttachment();
        a.setId(UUID.randomUUID());
        a.setIssueId(issueId);
        a.setFileName(fileName);
        a.setStorageKey(storageKey);
        a.setContentType(contentType);
        a.setFileSize(fileSize);
        a.setUploadedBy(uploadedBy);
        a.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(a);
        return a;
    }

    public void delete(UUID issueId, UUID attachmentId) {
        IssueAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || !a.getIssueId().equals(issueId)) {
            throw new ServiceException(404, "附件不存在");
        }
        attachmentMapper.deleteById(attachmentId);
    }
}
