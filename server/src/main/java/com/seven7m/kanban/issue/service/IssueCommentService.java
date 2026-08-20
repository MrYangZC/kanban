package com.seven7m.kanban.issue.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.issue.entity.IssueComment;
import com.seven7m.kanban.issue.mapper.IssueCommentMapper;
import com.seven7m.kanban.user.entity.User;
import com.seven7m.kanban.user.mapper.UserMapper;
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
public class IssueCommentService {

    private final IssueCommentMapper commentMapper;
    private final UserMapper userMapper;

    public List<IssueComment> list(UUID issueId) {
        return commentMapper.selectList(Wrappers.<IssueComment>lambdaQuery()
                        .eq(IssueComment::getIssueId, issueId)
                        .orderByAsc(IssueComment::getCreatedAt))
                .stream().peek(c -> {
                    User u = userMapper.selectById(c.getUserId());
                    if (u != null) c.setContent(u.getName());
                }).collect(Collectors.toList());
    }

    @Transactional
    public IssueComment add(UUID issueId, UUID userId, String content, UUID parentId) {
        if (content == null || content.trim().isEmpty()) {
            throw new ServiceException(400, "评论内容不能为空");
        }
        IssueComment c = new IssueComment();
        c.setId(UUID.randomUUID());
        c.setIssueId(issueId);
        c.setUserId(userId);
        c.setParentId(parentId);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(c);
        return c;
    }

    @Transactional
    public IssueComment update(UUID issueId, UUID commentId, UUID userId, String content) {
        IssueComment c = commentMapper.selectById(commentId);
        if (c == null || !c.getIssueId().equals(issueId)) {
            throw new ServiceException(404, "评论不存在");
        }
        if (!c.getUserId().equals(userId)) {
            throw new ServiceException(403, "无权限修改他人评论");
        }
        c.setContent(content);
        c.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(c);
        return c;
    }

    @Transactional
    public void delete(UUID issueId, UUID commentId, UUID userId) {
        IssueComment c = commentMapper.selectById(commentId);
        if (c == null || !c.getIssueId().equals(issueId)) {
            throw new ServiceException(404, "评论不存在");
        }
        if (c.getDeleted() != null && c.getDeleted() == 1) {
            return;
        }
        if (!c.getUserId().equals(userId)) {
            throw new ServiceException(403, "无权限删除他人评论");
        }
        commentMapper.deleteById(commentId);
    }
}
