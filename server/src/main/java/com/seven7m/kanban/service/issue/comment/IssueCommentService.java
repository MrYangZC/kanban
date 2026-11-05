package com.seven7m.kanban.service.issue.comment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.util.Uuids;
import com.seven7m.kanban.dto.issue.IssueActivityVO;
import com.seven7m.kanban.dto.issue.IssueTimelineVO;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.IssueTimelineMapper;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.support.ProjectWriteGuard;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
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
    private final IssueTimelineMapper timelineMapper;
    private final SysUserMapper userMapper;
    private final WorkItemRouter workItemRouter;
    private final ActivityTextResolver activityTextResolver;
    private final PermissionService permissionService;
    private final ProjectWriteGuard projectWriteGuard;

    /** 类别名（TASK/REQUIREMENT/BUG）；工作项不存在时由 router 统一抛 404 */
    private String categoryOf(UUID issueId) {
        return workItemRouter.categoryNameOrThrow(issueId);
    }

    /**
     * 评论 + 动态的时间线（分页，按时间倒序：最新在前）。
     *
     * <p>数据在数据库里 UNION ALL 合并后分页：评论与动态各一张表。
     * 之前是"两张表全量查出来再在内存合并"，工作项越久越慢，且一次拉几千条。
     */
    public IssueTimelineVO listActivities(UUID issueId, int page, int size) {
        String category = categoryOf(issueId);
        int p = Math.max(1, page);
        int s = Math.max(1, Math.min(size, 200));
        long total = timelineMapper.countTimeline(issueId, category);
        long commentTotal = timelineMapper.countComments(issueId, category);
        IssueTimelineVO result = new IssueTimelineVO();
        result.setTotal(total);
        result.setCommentCount(commentTotal);
        result.setPage(p);
        result.setSize(s);
        if (total == 0) {
            result.setList(List.of());
            return result;
        }
        List<Map<String, Object>> rows = timelineMapper.pageTimeline(issueId, category, s, (long) (p - 1) * s);

        // 批量取用户展示名，避免逐条查库
        Set<UUID> userIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            UUID actorId = asUuid(row.get("actor_id"));
            if (actorId != null) {
                userIds.add(actorId);
            }
        }
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        // 撤销可用性：最近一条交接动态 + 当前负责人，两者都满足才置 revocable（真正的校验在 revokeHandoff）。
        Map<String, Object> latestHandoff = timelineMapper.latestHandoff(issueId, category);
        UUID currentAssignee = workItemRouter.assigneeIdOf(issueId);
        String revocableActivityId = null;
        if (latestHandoff != null
                && "ASSIGNEE_CHANGED".equals(latestHandoff.get("action"))
                && currentAssignee != null) {
            String newValue = latestHandoff.get("new_value") == null ? null : latestHandoff.get("new_value").toString();
            if (currentAssignee.equals(activityTextResolver.userIdOf(newValue))) {
                revocableActivityId = String.valueOf(latestHandoff.get("id"));
            }
        }

        // 动态里存的是状态 code（{"status":"REQ_TODO"}），统一翻成中文名再返回
        Map<String, String> statusNames = activityTextResolver.loadStatusNames();
        List<IssueActivityVO> voList = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            IssueActivityVO vo = new IssueActivityVO();
            String id = row.get("id") == null ? null : row.get("id").toString();
            UUID actorId = asUuid(row.get("actor_id"));
            SysUser actor = actorId == null ? null : userMap.get(actorId);
            vo.setId(id == null ? null : UUID.fromString(id));
            vo.setType(str(row.get("type")));
            vo.setActorId(actorId);
            vo.setActorName(actor == null ? "未知" : actor.getDisplayName());
            vo.setActorAvatarUrl(actor == null ? null : actor.getAvatarUrl());
            vo.setContent(str(row.get("content")));
            vo.setParentId(asUuid(row.get("parent_id")));
            vo.setOldValue(activityTextResolver.toText(str(row.get("old_value")), statusNames));
            vo.setNewValue(activityTextResolver.toText(str(row.get("new_value")), statusNames));
            vo.setCreatedAt(asDateTime(row.get("created_at")));
            if (id != null && id.equals(revocableActivityId)) {
                vo.setRevocable(true);
            }
            voList.add(vo);
        }
        result.setList(voList);
        return result;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID asUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        // 复用 Uuids 的统一解析（失败返回 null），避免再写一份 try/catch
        return Uuids.parseOrNull(value.toString());
    }

    private static LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    @Transactional
    public IssueActivityVO addComment(UUID issueId, String content, UUID parentId) {
        // 归档项目只读：评论也属于"修改工作项"
        projectWriteGuard.requireWritable(workItemRouter.getProjectId(issueId));
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
        // 只能删自己发表的评论；系统管理员可代为清理。
        // 此前只要有 issue:comment 权限，任何人都能删别人的评论，且不留任何审计痕迹。
        UUID currentUser = UserContext.getUserId();
        boolean isAuthor = c.getUserId() != null && c.getUserId().equals(currentUser);
        if (!isAuthor && !permissionService.isSuperAdmin(currentUser)) {
            throw new ServiceException(403, "只能删除自己发表的评论");
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
