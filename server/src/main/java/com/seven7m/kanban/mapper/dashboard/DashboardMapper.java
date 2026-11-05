package com.seven7m.kanban.mapper.dashboard;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.dto.dashboard.DashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 统计报表只读聚合查询。所有方法均为 SELECT，不触碰写路径。
 *
 * <p>工作项已从单表 issue 拆分为 task / requirement / bug 三张物理表，
 * 因此所有统计均通过三表 UNION ALL 的公共 CTE（w）聚合，仅取三表同构的公共列。
 *
 * <p>口径统一约定（与列表页保持一致）：
 * <ul>
 *   <li>一律排除 {@code archived = true}：否则度量页会把已归档工作项算进去，和列表对不上；</li>
 *   <li>完成时间用 {@code done_at}（首次进入 DONE 类别的时间），不用 {@code updated_at}
 *       ——后者任何编辑都会变，周期时间会被"改个标题"凭空拉长；</li>
 *   <li>完成度判定统一按 {@code issue_status.category = 'DONE'}。</li>
 * </ul>
 */
@Mapper
public interface DashboardMapper extends BaseMapper<Object> {

    /** 状态分布：JOIN issue_status 按名称聚合 */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT s.name AS name, COUNT(*) AS count
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            GROUP BY s.name, s.sort_order
            ORDER BY s.sort_order
            """)
    List<DashboardVO.NameCount> statusDist(@Param("projectId") UUID projectId);

    /** 优先级分布：P0~P3 */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT COALESCE(w.priority, 'NONE') AS name, COUNT(*) AS count
            FROM w
            GROUP BY w.priority
            ORDER BY w.priority
            """)
    List<DashboardVO.NameCount> priorityDist(@Param("projectId") UUID projectId);

    /** 按负责人分布的工作量（带 display_name 与头像，供度量页按人展示） */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT COALESCE(NULLIF(u.display_name, ''), u.username, '未分配') AS name,
                   u.avatar_url AS avatar_url,
                   COUNT(*) AS count
            FROM w
            LEFT JOIN sys_user u ON u.id = w.assignee_id
            GROUP BY u.display_name, u.username, u.avatar_url
            ORDER BY count DESC
            """)
    List<DashboardVO.AssigneeCount> assigneeDist(@Param("projectId") UUID projectId);

    /** 概览：总数 / 待处理(TODO) / 处理中(IN_PROGRESS) / 已完成(DONE) / 逾期(due_date < now 且未完成) */
    @Select("""
            WITH w AS (
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT status_id, priority, assignee_id, due_date, created_at, updated_at, done_at
                FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE s.category = 'TODO') AS todo,
                COUNT(*) FILTER (WHERE s.category = 'IN_PROGRESS') AS in_progress,
                COUNT(*) FILTER (WHERE s.category = 'DONE') AS done,
                COUNT(*) FILTER (WHERE w.due_date IS NOT NULL AND w.due_date < now() AND s.category <> 'DONE') AS overdue
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            """)
    DashboardSummaryView summary(@Param("projectId") UUID projectId);

    /**
     * 分类型概览：需求 / 缺陷 / 任务各一张表，用 UNION ALL 带出类型标签后按类型聚合，
     * 口径与 {@link #summary} 完全一致（同一套 issue_status.category 判定）。
     */
    @Select("""
            WITH w AS (
                SELECT 'TASK' AS type_code, status_id, due_date FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT 'REQUIREMENT' AS type_code, status_id, due_date FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT 'BUG' AS type_code, status_id, due_date FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT
                w.type_code AS type_code,
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE s.category = 'TODO') AS todo,
                COUNT(*) FILTER (WHERE s.category = 'IN_PROGRESS') AS in_progress,
                COUNT(*) FILTER (WHERE s.category = 'DONE') AS done,
                COUNT(*) FILTER (WHERE w.due_date IS NOT NULL AND w.due_date < now() AND s.category <> 'DONE') AS overdue
            FROM w
            JOIN issue_status s ON s.id = w.status_id
            GROUP BY w.type_code
            """)
    List<TypeSummaryView> typeSummaries(@Param("projectId") UUID projectId);

    /**
     * 已解决工作项的周期时间（小时）：完成时间 {@code done_at} - 创建时间。
     * 只用 done_at 非空的行，避免把"编辑过的未完成项"算进来。
     */
    @Select("""
            WITH w AS (
                SELECT created_at, done_at FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT created_at, done_at FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT created_at, done_at FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT
                COALESCE(AVG(EXTRACT(EPOCH FROM (w.done_at - w.created_at)) / 3600), 0) AS avg_hours,
                COALESCE(MAX(EXTRACT(EPOCH FROM (w.done_at - w.created_at)) / 3600), 0) AS max_hours
            FROM w
            WHERE w.done_at IS NOT NULL
            """)
    CycleTimeView cycleTime(@Param("projectId") UUID projectId);

    /** 每日完成数（按 done_at 日期，仅已完成项） */
    @Select("""
            WITH w AS (
                SELECT done_at FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT done_at FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT done_at FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT TO_CHAR(w.done_at, 'YYYY-MM-DD') AS date, COUNT(*) AS completed
            FROM w
            WHERE w.done_at IS NOT NULL
              AND w.done_at >= #{start}
            GROUP BY TO_CHAR(w.done_at, 'YYYY-MM-DD')
            ORDER BY date
            """)
    List<DateCountView> dailyCompleted(@Param("projectId") UUID projectId,
                                       @Param("start") LocalDateTime start);

    /** 每日新建数（燃尽图的"总工作量"曲线来源，按 created_at 累计） */
    @Select("""
            WITH w AS (
                SELECT created_at FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT created_at FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT created_at FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT TO_CHAR(w.created_at, 'YYYY-MM-DD') AS date, COUNT(*) AS created
            FROM w
            WHERE w.created_at >= #{start}
            GROUP BY TO_CHAR(w.created_at, 'YYYY-MM-DD')
            ORDER BY date
            """)
    List<DateCountView> dailyCreated(@Param("projectId") UUID projectId,
                                     @Param("start") LocalDateTime start);

    /** 窗口开始前已创建的总数（燃尽图起点基线） */
    @Select("""
            WITH w AS (
                SELECT created_at FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT created_at FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT created_at FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT COUNT(*) AS count FROM w WHERE w.created_at < #{start}
            """)
    long countCreatedBefore(@Param("projectId") UUID projectId, @Param("start") LocalDateTime start);

    /** 窗口开始前已完成的总数（燃尽图起点基线） */
    @Select("""
            WITH w AS (
                SELECT done_at FROM task WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT done_at FROM requirement WHERE project_id = #{projectId} AND archived = false
                UNION ALL
                SELECT done_at FROM bug WHERE project_id = #{projectId} AND archived = false
            )
            SELECT COUNT(*) AS count FROM w WHERE w.done_at IS NOT NULL AND w.done_at < #{start}
            """)
    long countDoneBefore(@Param("projectId") UUID projectId, @Param("start") LocalDateTime start);

    class DashboardSummaryView {
        public long total;
        public long todo;
        public long inProgress;
        public long done;
        public long overdue;
    }

    class TypeSummaryView {
        public String typeCode;
        public long total;
        public long todo;
        public long inProgress;
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
