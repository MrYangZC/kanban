package com.seven7m.kanban.service.issue.comment;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.dto.issue.IssueActivityVO;
import com.seven7m.kanban.dto.issue.IssueTimelineVO;
import com.seven7m.kanban.entity.issue.comment.IssueComment;

import java.util.UUID;

/**
 * 工作项评论与动态服务接口。
 *
 * <p>评论本身是「单实体（issue_comment）+ 单主 Mapper」的增删，因此继承 {@link IService}
 * 复用 MyBatis-Plus 的按 id 取 / 保存 / 计数 / 删除能力；时间线是评论与动态的合并查询，
 * 走 {@code IssueTimelineMapper} 的定制 SQL，不在通用 CRUD 范围内。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.issue.comment.impl.IssueCommentServiceImpl}。
 */
public interface IssueCommentService extends IService<IssueComment> {

    /**
     * 评论 + 动态的时间线（分页，按时间倒序：最新在前）。
     *
     * <p>数据在数据库里 UNION ALL 合并后分页，不在内存里合并，避免老工作项一次拉几千条。
     */
    IssueTimelineVO listActivities(UUID issueId, int page, int size);

    /** 新增评论（或嵌套回复）；parentId 非空时校验父评论存在且同属该工作项 */
    IssueActivityVO addComment(UUID issueId, String content, UUID parentId);

    /** 删除评论：只有作者本人或系统管理员可删；有子回复时拒绝 */
    void deleteComment(UUID issueId, UUID commentId);
}
