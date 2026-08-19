package com.seven7m.kanban.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.dashboard.dto.DashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 统计报表只读聚合查询。所有方法均为 SELECT，不触碰 issue 写路径。
 */
@Mapper
public interface DashboardMapper extends BaseMapper<Object> {

    /** 状态分布：JOIN issue_status 按名称聚合 */
    @Select("""
            SELECT s.name AS name, COUNT(i.id) AS count
            FROM issue i
            JOIN issue_status s ON s.id = i.status_id
            WHERE i.project_id = #{projectId} AND i.deleted = 0
            GROUP BY s.name, s.sort_order
            ORDER BY s.sort_order
            """)
    List<DashboardVO.NameCount> statusDist(@Param("projectId") java.util.UUID projectId);

    /** 优先级分布：P0~P3 */
    @Select("""
            SELECT COALESCE(i.priority, 'NONE') AS name, COUNT(i.id) AS count
            FROM issue i
            WHERE i.project_id = #{projectId} AND i.deleted = 0
            GROUP BY i.priority
            ORDER BY i.priority
            """)
    List<DashboardVO.NameCount> priorityDist(@Param("projectId") java.util.UUID projectId);

    /** 按负责人分布的工作量 */
    @Select("""
            SELECT COALESCE(u.username, '未分配') AS name, COUNT(i.id) AS count
            FROM issue i
            LEFT JOIN sys_user u ON u.id = i.assignee_id
            WHERE i.project_id = #{projectId} AND i.deleted = 0
            GROUP BY u.username
            ORDER BY count DESC
            """)
    List<DashboardVO.NameCount> assigneeDist(@Param("projectId") java.util.UUID projectId);

    /** 概览：总数 / 已完成(category=DONE) / 逾期(due_date < now 且未完成) */
    @Select("""
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE s.category = 'DONE') AS done,
                COUNT(*) FILTER (WHERE i.due_date IS NOT NULL AND i.due_date < now() AND s.category <> 'DONE') AS overdue
            FROM issue i
            JOIN issue_status s ON s.id = i.status_id
            WHERE i.project_id = #{projectId} AND i.deleted = 0
            """)
    DashboardSummaryView summary(@Param("projectId") java.util.UUID projectId);

    /** 已完成 Issue 的周期时间（小时）：updated_at - created_at */
    @Select("""
            SELECT
                COALESCE(AVG(EXTRACT(EPOCH FROM (i.updated_at - i.created_at)) / 3600), 0) AS avg_hours,
                COALESCE(MAX(EXTRACT(EPOCH FROM (i.updated_at - i.created_at)) / 3600), 0) AS max_hours
            FROM issue i
            JOIN issue_status s ON s.id = i.status_id
            WHERE i.project_id = #{projectId} AND i.deleted = 0 AND s.category = 'DONE'
            """)
    CycleTimeView cycleTime(@Param("projectId") java.util.UUID projectId);

    /** 每日完成数（按 updated_at 日期，仅 DONE） */
    @Select("""
            SELECT TO_CHAR(i.updated_at, 'YYYY-MM-DD') AS date, COUNT(*) AS completed
            FROM issue i
            JOIN issue_status s ON s.id = i.status_id
            WHERE i.project_id = #{projectId} AND i.deleted = 0
              AND s.category = 'DONE'
              AND i.updated_at >= NOW() - INTERVAL '14 days'
            GROUP BY TO_CHAR(i.updated_at, 'YYYY-MM-DD')
            ORDER BY date
            """)
    List<DateCountView> dailyCompleted(@Param("projectId") java.util.UUID projectId);

    /** 每日累计创建数（用于计算 remaining 基线） */
    @Select("""
            SELECT TO_CHAR(i.created_at, 'YYYY-MM-DD') AS date, COUNT(*) AS created
            FROM issue i
            WHERE i.project_id = #{projectId} AND i.deleted = 0
              AND i.created_at >= NOW() - INTERVAL '14 days'
            GROUP BY TO_CHAR(i.created_at, 'YYYY-MM-DD')
            ORDER BY date
            """)
    List<DateCountView> dailyCreated(@Param("projectId") java.util.UUID projectId);

    /** 总完成数（用于 burndown 收尾） */
    @Select("""
            SELECT COUNT(*) AS count
            FROM issue i
            JOIN issue_status s ON s.id = i.status_id
            WHERE i.project_id = #{projectId} AND i.deleted = 0 AND s.category = 'DONE'
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
