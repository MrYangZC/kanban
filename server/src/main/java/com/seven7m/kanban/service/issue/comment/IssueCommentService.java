package com.seven7m.kanban.service.issue.comment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.issue.IssueActivityVO;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
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
    private final IssueActivityMapper activityMapper;
    private final SysUserMapper userMapper;

    /** 评论列表（动态&评论 Tab 用，含评论 + 状态变更等活动） */
    public List<IssueActivityVO> listActivities(UUID issueId) {
        // 评论（保留已逻辑删除的父评论，供前端渲染「已删除」占位；子回复不受影响）
        List<IssueActivityVO> comments = commentMapper.selectList(Wrappers.<IssueComment>lambdaQuery()
                        .eq(IssueComment::getIssueId, issueId)
                        .orderByAsc(IssueComment::getCreatedAt))
                .stream().map(c -> {
                    IssueActivityVO vo = new IssueActivityVO();
                    SysUser u = userMapper.selectById(c.getUserId());
                    vo.setId(c.getId());
                    vo.setType("COMMENT");
                    vo.setActorId(c.getUserId());
                    vo.setActorName(u == null ? "未知" : u.getDisplayName());
                    vo.setParentId(c.getParentId());
                    vo.setDeleted(c.getDeleted());
                    vo.setContent(c.getContent() == null ? "" : c.getContent());
                    vo.setCreatedAt(c.getCreatedAt());
                    return vo;
                }).collect(Collectors.toList());

        // 活动（状态变更等）
        List<IssueActivityVO> acts = activityMapper.selectList(Wrappers.<IssueActivity>lambdaQuery()
                        .eq(IssueActivity::getIssueId, issueId)
                        .orderByAsc(IssueActivity::getCreatedAt))
                .stream().map(a -> {
                    IssueActivityVO vo = new IssueActivityVO();
                    SysUser u = userMapper.selectById(a.getActorId());
                    vo.setId(a.getId());
                    vo.setType(a.getAction());
                    vo.setActorId(a.getActorId());
                    vo.setActorName(u == null ? "未知" : u.getDisplayName());
                    vo.setOldValue(a.getOldValue());
                    vo.setNewValue(a.getNewValue());
                    vo.setCreatedAt(a.getCreatedAt());
                    return vo;
                }).collect(Collectors.toList());

        comments.addAll(acts);
        comments.sort((x, y) -> {
            if (x.getCreatedAt() == null) return -1;
            if (y.getCreatedAt() == null) return 1;
            return x.getCreatedAt().compareTo(y.getCreatedAt());
        });
        return comments;
    }

    @Transactional
    public IssueActivityVO addComment(UUID issueId, String content, UUID parentId) {
        IssueComment c = new IssueComment();
        c.setId(UUID.randomUUID());
        c.setIssueId(issueId);
        c.setUserId(UserContext.getUserId());
        c.setParentId(parentId);   // 嵌套回复：顶层评论为 null
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        c.setDeleted(0);
        commentMapper.insert(c);

        SysUser u = userMapper.selectById(c.getUserId());
        IssueActivityVO vo = new IssueActivityVO();
        vo.setId(c.getId());
        vo.setType("COMMENT");
        vo.setActorId(c.getUserId());
        vo.setActorName(u == null ? "未知" : u.getDisplayName());
        vo.setParentId(c.getParentId());
        vo.setDeleted(0);
        vo.setContent(c.getContent());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    @Transactional
    public void deleteComment(UUID issueId, UUID commentId) {
        IssueComment c = commentMapper.selectById(commentId);
        if (c == null || !c.getIssueId().equals(issueId)) {
            return;
        }
        commentMapper.deleteById(commentId);
    }
}
