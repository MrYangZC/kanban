package com.seven7m.kanban.mapper.issue;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工作项时间线（评论 + 动态）的合并查询。
 *
 * <p>评论在 {@code issue_comment}、动态在 {@code issue_activity}，两者此前是"各自全量查出来再在内存里合并排序"，
 * 一个持续半年的工作项会一次拉几千条。这里用 UNION ALL 在数据库侧合并并分页，
 * 保证响应体与查询量都与数据量脱钩。
 *
 * <p>返回 Map 而不是实体：两张表字段不同构，用 {@code NULL} 占位比造一个中间实体更直观。
 * 调用方一律先经 {@link TimelineRow#from} / {@link HandoffMarker#from} 转成类型化对象，
 * 列名只在那两个类里写一次（此前是调用方按 {@code row.get("new_value")} 取值，
 * 列别名写错只会在线上表现为"新值总是空"）。
 * jsonb 列显式 {@code ::text}，避免 PGobject 直接落到调用方。
 */
@Mapper
public interface IssueTimelineMapper {

    /** 时间线一页（按时间倒序：最新在前，前端"加载更早"往后续页） */
    @Select("""
            <script>
            SELECT 'COMMENT' AS type, c.id AS id, c.user_id AS actor_id, NULL AS action,
                   NULL AS old_value, NULL AS new_value, c.content AS content,
                   c.parent_id AS parent_id, c.created_at AS created_at
            FROM issue_comment c
            WHERE c.issue_id = #{issueId} AND c.category = #{category}
            UNION ALL
            SELECT a.action AS type, a.id AS id, a.actor_id AS actor_id, a.action AS action,
                   a.old_value::text AS old_value, a.new_value::text AS new_value, NULL AS content,
                   NULL AS parent_id, a.created_at AS created_at
            FROM issue_activity a
            WHERE a.issue_id = #{issueId} AND a.category = #{category}
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> pageTimeline(@Param("issueId") UUID issueId,
                                           @Param("category") String category,
                                           @Param("size") int size,
                                           @Param("offset") long offset);

    /** 时间线总条数（评论数 + 动态数） */
    @Select("""
            SELECT (SELECT COUNT(*) FROM issue_comment WHERE issue_id = #{issueId} AND category = #{category})
                 + (SELECT COUNT(*) FROM issue_activity WHERE issue_id = #{issueId} AND category = #{category})
            """)
    long countTimeline(@Param("issueId") UUID issueId, @Param("category") String category);

    /** 评论条数（时间线角标用，避免前端只能数当前页） */
    @Select("""
            SELECT COUNT(*) FROM issue_comment
            WHERE issue_id = #{issueId} AND category = #{category}
            """)
    long countComments(@Param("issueId") UUID issueId, @Param("category") String category);

    /**
     * 最近一条"交接 / 撤销交接"动态，用于判断撤销按钮是否可用。
     * 单独查一条，不用把全部动态加载到内存里找最后一条。
     */
    @Select("""
            SELECT a.id AS id, a.action AS action, a.new_value::text AS new_value,
                   a.created_at AS created_at
            FROM issue_activity a
            WHERE a.issue_id = #{issueId} AND a.category = #{category}
              AND a.action IN ('ASSIGNEE_CHANGED', 'HANDOFF_REVOKED')
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT 1
            """)
    Map<String, Object> latestHandoff(@Param("issueId") UUID issueId, @Param("category") String category);
}
