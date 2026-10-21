package com.seven7m.kanban.service.issue.comment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.issue.IssueActivityVO;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueCommentService {

    private final IssueCommentMapper commentMapper;
    private final IssueActivityMapper activityMapper;
    private final SysUserMapper userMapper;
    private final WorkItemRouter workItemRouter;
    private final ActivityTextResolver activityTextResolver;

    /** 根据工作项 id 判定所属类别（TASK/REQUIREMENT/BUG），不存在抛 404 */
    private String categoryOf(UUID issueId) {
        ItemCategory c = workItemRouter.categoryOf(issueId);
        if (c == null) throw new ServiceException(404, "工作项不存在");
        return c.name();
    }

    /** 评论列表 */
    public List<IssueActivityVO> listActivities(UUID issueId) {
        List<IssueComment> comments = commentMapper.selectList(Wrappers.<IssueComment>lambdaQuery()
                .eq(IssueComment::getIssueId, issueId)
                .orderByAsc(IssueComment::getCreatedAt));
        List<IssueActivity> activities = activityMapper.selectList(Wrappers.<IssueActivity>lambdaQuery()
                .eq(IssueActivity::getIssueId, issueId)
                .orderByAsc(IssueActivity::getCreatedAt));

        // 批量取用户展示名：替代逐条 selectById 的 N+1（评论数+动态数可能几十上百条）
        Set<UUID> userIds = new HashSet<>();
        for (IssueComment c : comments) {
            if (c.getUserId() != null) userIds.add(c.getUserId());
        }
        for (IssueActivity a : activities) {
            if (a.getActorId() != null) userIds.add(a.getActorId());
        }
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        List<IssueActivityVO> result = new ArrayList<>(comments.size() + activities.size());
        for (IssueComment c : comments) {
            IssueActivityVO vo = new IssueActivityVO();
            SysUser u = c.getUserId() == null ? null : userMap.get(c.getUserId());
            vo.setId(c.getId());
            vo.setType("COMMENT");
            vo.setActorId(c.getUserId());
            vo.setActorName(u == null ? "未知" : u.getDisplayName());
            vo.setActorAvatarUrl(u == null ? null : u.getAvatarUrl());
            vo.setParentId(c.getParentId());
            vo.setContent(c.getContent() == null ? "" : c.getContent());
            vo.setCreatedAt(c.getCreatedAt());
            result.add(vo);
        }
        // 动态里存的是状态 code（{"status":"REQ_TODO"}），这里统一翻成中文名再返回，
        // 否则前端动态/评论列表会直接显示英文 code
        Map<String, String> statusNames = activityTextResolver.loadStatusNames();
        for (IssueActivity a : activities) {
            IssueActivityVO vo = new IssueActivityVO();
            SysUser u = a.getActorId() == null ? null : userMap.get(a.getActorId());
            vo.setId(a.getId());
            vo.setType(a.getAction());
            vo.setActorId(a.getActorId());
            vo.setActorName(u == null ? "未知" : u.getDisplayName());
            vo.setActorAvatarUrl(u == null ? null : u.getAvatarUrl());
            vo.setOldValue(activityTextResolver.toText(a.getOldValue(), statusNames));
            vo.setNewValue(activityTextResolver.toText(a.getNewValue(), statusNames));
            vo.setCreatedAt(a.getCreatedAt());
            result.add(vo);
        }

        result.sort((x, y) -> {
            if (x.getCreatedAt() == null) return -1;
            if (y.getCreatedAt() == null) return 1;
            return x.getCreatedAt().compareTo(y.getCreatedAt());
        });
        return result;
    }

    @Transactional
    public IssueActivityVO addComment(UUID issueId, String content, UUID parentId) {
        if (parentId != null) {
            // 父评论必须存在且同属该工作项，否则会形成跨项错乱楼层
            IssueComment parent = commentMapper.selectById(parentId);
            if (parent == null) throw new ServiceException(400, "父评论不存在");
            if (!issueId.equals(parent.getIssueId())) {
                throw new ServiceException(400, "父评论不属于当前工作项，无法回复");
            }
        }
        IssueComment c = new IssueComment();
        c.setId(UUID.randomUUID());
        c.setIssueId(issueId);
        // 三表拆分后必须显式落类别，否则会取列默认值 TASK，
        // 导致需求/缺陷的评论在详情页计数不到、删除时清不掉
        c.setCategory(categoryOf(issueId));
        c.setUserId(UserContext.getUserId());
        c.setParentId(parentId);   // 嵌套回复：顶层评论为 null
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(c);

        SysUser u = userMapper.selectById(c.getUserId());
        IssueActivityVO vo = new IssueActivityVO();
        vo.setId(c.getId());
        vo.setType("COMMENT");
        vo.setActorId(c.getUserId());
        vo.setActorName(u == null ? "未知" : u.getDisplayName());
        vo.setActorAvatarUrl(u == null ? null : u.getAvatarUrl());
        vo.setParentId(c.getParentId());
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
        // 有子回复时先给明确提示，避免外键 fk_issue_comment_parent 抛出「数据不符合约束」的模糊错误
        long children = commentMapper.selectCount(Wrappers.<IssueComment>lambdaQuery()
                .eq(IssueComment::getParentId, commentId));
        if (children > 0) {
            throw new ServiceException(400, "该评论下还有 " + children + " 条回复，请先删除回复后再删除");
        }
        commentMapper.deleteById(commentId);
    }
}
