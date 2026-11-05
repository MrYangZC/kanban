package com.seven7m.kanban.mapper.issue;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 工作项时间线（评论 + 动态）的合并查询。
 *
 * <p>评论在 {@code issue_comment}、动态在 {@code issue_activity}，两者此前是"各自全量查出来再在内存里合并排序"，
 * 一个持续半年的工作项会一次拉几千条。这里用 UNION ALL 在数据库侧合并并分页，
 * 保证响应体与查询量都与数据量脱钩。
 *
 * <p>结果按 POJO 返回（{@link TimelineRow} / {@link HandoffMarker}），列到属性的映射用 {@code @Results}
 * 显式声明：两张表字段不同构，UNION 里用 {@code NULL} 占位比造一个中间实体更直观，但**不允许**
 * 再把 {@code Map} 交给业务层按字符串键取值。别名与属性的对应关系由
 * {@code TimelineMapperMappingTest} 静态校验。
 *
 * <p>jsonb 列显式 {@code ::text}，避免 PGobject 直接落到调用方。
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
    @Results(id = "timelineRow", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "type", property = "type"),
            @Result(column = "actor_id", property = "actorId"),
            @Result(column = "content", property = "content"),
            @Result(column = "parent_id", property = "parentId"),
            @Result(column = "old_value", property = "oldValue"),
            @Result(column = "new_value", property = "newValue"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<TimelineRow> pageTimeline(@Param("issueId") UUID issueId,
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
    @Results(id = "handoffMarker", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "action", property = "action"),
            @Result(column = "new_value", property = "newValue")
    })
    HandoffMarker latestHandoff(@Param("issueId") UUID issueId, @Param("category") String category);
}
