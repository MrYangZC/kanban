package com.seven7m.kanban.service.issue.comment.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.constant.DisplayNames;
import com.seven7m.kanban.common.constant.PageLimits;
import com.seven7m.kanban.common.domain.NameIndex;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.issue.IssueActivityVO;
import com.seven7m.kanban.dto.issue.IssueTimelineVO;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.HandoffMarker;
import com.seven7m.kanban.mapper.issue.IssueTimelineMapper;
import com.seven7m.kanban.mapper.issue.TimelineRow;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.issue.comment.ActivityTextResolver;
import com.seven7m.kanban.service.issue.comment.IssueCommentService;
import com.seven7m.kanban.service.support.ProjectWriteGuard;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 评论与动态服务实现。
 *
 * <p>评论表的增删改查交给 {@link ServiceImpl}（{@code getById} / {@code save} / {@code count} /
 * {@code removeById}）；时间线的合并分页、以及"操作人头像/名称"的批量装配留在本类，
 * 因为两者都不是单表通用 CRUD。
 */
@Service
@RequiredArgsConstructor
public class IssueCommentServiceImpl extends ServiceImpl<IssueCommentMapper, IssueComment>
        implements IssueCommentService {

    /**
     * 时间线单页上限。
     *
     * <p>与通用列表的 {@link PageLimits#ADMIN_MAX_SIZE}（100）分开：时间线是"滚动加载更早"，
     * 前端一次追加一屏，200 是实测既能少发请求、响应体又不至于过重（每行还要带操作人头像）的上限。
     */
    private static final int TIMELINE_MAX_PAGE_SIZE = 200;

    /** 时间线里评论行的类型值，必须与 {@code IssueTimelineMapper} 的 UNION ALL 里 'COMMENT' 保持一致 */
    private static final String TIMELINE_TYPE_COMMENT = "COMMENT";

    /** 操作人已不存在（或未记录）时的展示名，与前端"未知用户"的口径保持一致 */

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
    @Override
    public IssueTimelineVO listActivities(UUID issueId, int page, int size) {
        String category = categoryOf(issueId);
        int p = Math.max(PageLimits.MIN_PAGE, page);
        int s = Math.max(PageLimits.MIN_PAGE, Math.min(size, TIMELINE_MAX_PAGE_SIZE));
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
        // 结果已是 POJO（列到属性的映射在 mapper 的 @Results 上声明）
        List<TimelineRow> rows = timelineMapper.pageTimeline(issueId, category, s, (long) (p - 1) * s);

        // 批量取用户展示名，避免逐条查库
        Set<UUID> userIds = rows.stream()
                .map(TimelineRow::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        // 撤销可用性：最近一条交接动态 + 当前负责人，两者都满足才置 revocable（真正的校验在 revokeHandoff）。
        HandoffMarker latestHandoff = timelineMapper.latestHandoff(issueId, category);
        UUID currentAssignee = workItemRouter.assigneeIdOf(issueId);
        String revocableActivityId = null;
        if (latestHandoff != null && latestHandoff.isAssigneeChanged() && currentAssignee != null
                && currentAssignee.equals(activityTextResolver.userIdOf(latestHandoff.getNewValue()))) {
            revocableActivityId = String.valueOf(latestHandoff.getId());
        }

        // 动态里存的是状态 code（{"status":"REQ_TODO"}），统一翻成中文名再返回
        NameIndex<String> statusNames = activityTextResolver.loadStatusNames();
        List<IssueActivityVO> voList = new ArrayList<>(rows.size());
        for (TimelineRow row : rows) {
            IssueActivityVO vo = new IssueActivityVO();
            UUID actorId = row.getActorId();
            SysUser actor = actorId == null ? null : userMap.get(actorId);
            vo.setId(row.getId());
            vo.setType(row.getType());
            vo.setActorId(actorId);
            vo.setActorName(actor == null ? DisplayNames.UNKNOWN_USER : actor.getDisplayName());
            vo.setActorAvatarUrl(actor == null ? null : actor.getAvatarUrl());
            vo.setContent(row.getContent());
            vo.setParentId(row.getParentId());
            vo.setOldValue(activityTextResolver.toText(row.getOldValue(), statusNames));
            vo.setNewValue(activityTextResolver.toText(row.getNewValue(), statusNames));
            vo.setCreatedAt(row.getCreatedAt());
            if (row.getId() != null && row.getId().toString().equals(revocableActivityId)) {
                vo.setRevocable(true);
            }
            voList.add(vo);
        }
        result.setList(voList);
        return result;
    }

    @Override
    @Transactional
    public IssueActivityVO addComment(UUID issueId, String content, UUID parentId) {
        // 归档项目只读：评论也属于"修改工作项"
        projectWriteGuard.requireWritable(workItemRouter.getProjectId(issueId));
        if (parentId != null) {
            // 父评论必须存在且同属该工作项，否则会形成跨项错乱楼层
            IssueComment parent = getById(parentId);
            if (parent == null) {
                throw new ServiceException(ErrorCode.COMMENT_PARENT_NOT_FOUND);
            }
            if (!issueId.equals(parent.getIssueId())) {
                throw new ServiceException(ErrorCode.COMMENT_PARENT_CROSS_ITEM);
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
        save(c);

        SysUser u = userMapper.selectById(c.getUserId());
        IssueActivityVO vo = new IssueActivityVO();
        vo.setId(c.getId());
        vo.setType(TIMELINE_TYPE_COMMENT);
        vo.setActorId(c.getUserId());
        vo.setActorName(u == null ? DisplayNames.UNKNOWN_USER : u.getDisplayName());
        vo.setActorAvatarUrl(u == null ? null : u.getAvatarUrl());
        vo.setParentId(c.getParentId());
        vo.setContent(c.getContent());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    /**
     * 删除评论。
     *
     * <p>归档项目只读，删除同样被拦（与被拦的新建/编辑路径一致）。
     */
    @Override
    @Transactional
    public void deleteComment(UUID issueId, UUID commentId) {
        IssueComment c = getById(commentId);
        if (c == null || !c.getIssueId().equals(issueId)) {
            return;
        }
        // 归档项目只读：删除评论同样属于修改工作项，与新建路径（addComment）同一套拦截
        projectWriteGuard.requireWritable(workItemRouter.getProjectId(issueId));
        // 只能删自己发表的评论；系统管理员可代为清理。
        // 此前只要有 issue:comment 权限，任何人都能删别人的评论，且不留任何审计痕迹。
        UUID currentUser = UserContext.getUserId();
        boolean isAuthor = c.getUserId() != null && c.getUserId().equals(currentUser);
        if (!isAuthor && !permissionService.isSuperAdmin(currentUser)) {
            throw new ServiceException(ErrorCode.COMMENT_DELETE_FORBIDDEN);
        }
        // 有子回复时先给明确提示，避免外键 fk_issue_comment_parent 抛出「数据不符合约束」的模糊错误
        long children = count(Wrappers.<IssueComment>lambdaQuery()
                .eq(IssueComment::getParentId, commentId));
        if (children > 0) {
            throw new ServiceException(ErrorCode.COMMENT_HAS_REPLIES, children);
        }
        removeById(commentId);
    }
}
