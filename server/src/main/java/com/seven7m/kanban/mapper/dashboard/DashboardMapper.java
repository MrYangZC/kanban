package com.seven7m.kanban.mapper.dashboard;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.dto.dashboard.DashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 统计报表只读聚合查询。所有方法均为 SELECT，不触碰写路径。
 *
 * 工作项已从单表 issue 拆分为 task / requirement / bug 三张物理表，
 * 因此所有统计均通过三表 UNION ALL 的公共 CTE（w）聚合，
 * 仅取三表同构的公共列，避免统计遗漏任意类型的工作项。
 */
@Mapper
public interface DashboardMapper extends BaseMapper<Object> {

    /** 状态分布：JOIN issue_status 按名称聚合 */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM task WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM requirement WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM bug WHERE project_id = #{projectId}
            )
            SELECT s.name AS name, COUNT(*) AS count
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            GROUP BY s.name, s.sort_order
            ORDER BY s.sort_order
            """)
    List<DashboardVO.NameCount> statusDist(@Param("projectId") java.util.UUID projectId);

    /** 优先级分布：P0~P3 */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM task WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM requirement WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM bug WHERE project_id = #{projectId}
            )
            SELECT COALESCE(w.priority, 'NONE') AS name, COUNT(*) AS count
            FROM w
            GROUP BY w.priority
            ORDER BY w.priority
            """)
    List<DashboardVO.NameCount> priorityDist(@Param("projectId") java.util.UUID projectId);

    /** 按负责人分布的工作量（带 display_name 与头像，供度量页按人展示） */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM task WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM requirement WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM bug WHERE project_id = #{projectId}
            )
            SELECT COALESCE(NULLIF(u.display_name, ''), u.username, '未分配') AS name,
                   u.avatar_url AS avatar_url,
                   COUNT(*) AS count
            FROM w
            LEFT JOIN sys_user u ON u.id = w.assignee_id
            GROUP BY u.display_name, u.username, u.avatar_url
            ORDER BY count DESC
            """)
    List<DashboardVO.AssigneeCount> assigneeDist(@Param("projectId") java.util.UUID projectId);

    /** 概览：总数 / 已完成(category=DONE) / 逾期(due_date < now 且未完成) */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM task WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM requirement WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM bug WHERE project_id = #{projectId}
            )
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE s.category = 'DONE') AS done,
                COUNT(*) FILTER (WHERE w.due_date IS NOT NULL AND w.due_date < now() AND s.category <> 'DONE') AS overdue
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            """)
    DashboardSummaryView summary(@Param("projectId") java.util.UUID projectId);

    /** 已完成工作项的周期时间（小时）：updated_at - created_at */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM task WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM requirement WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM bug WHERE project_id = #{projectId}
            )
            SELECT
                COALESCE(AVG(EXTRACT(EPOCH FROM (w.updated_at - w.created_at)) / 3600), 0) AS avg_hours,
                COALESCE(MAX(EXTRACT(EPOCH FROM (w.updated_at - w.created_at)) / 3600), 0) AS max_hours
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            WHERE s.category = 'DONE'
            """)
    CycleTimeView cycleTime(@Param("projectId") java.util.UUID projectId);

    /** 每日完成数（按 updated_at 日期，仅 DONE） */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM task WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM requirement WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM bug WHERE project_id = #{projectId}
            )
            SELECT TO_CHAR(w.updated_at, 'YYYY-MM-DD') AS date, COUNT(*) AS completed
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            WHERE s.category = 'DONE'
              AND w.updated_at >= NOW() - INTERVAL '14 days'
            GROUP BY TO_CHAR(w.updated_at, 'YYYY-MM-DD')
            ORDER BY date
            """)
    List<DateCountView> dailyCompleted(@Param("projectId") java.util.UUID projectId);

    /** 总完成数（用于 burndown 收尾） */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM task WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM requirement WHERE project_id = #{projectId}
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at
                FROM bug WHERE project_id = #{projectId}
            )
            SELECT COUNT(*) AS count
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            WHERE s.category = 'DONE'
            """)
    long totalDone(@Param("projectId") java.util.UUID projectId);

    class DashboardSummaryView {
        public long total;
        public long done;
        public long overdue;
    }

    class CycleTimeView {
        public double avgHours;
        public double maxHours;
    }

    class DateCountView {
        public String date;
        public long completed;
        public long created;
    }
}
