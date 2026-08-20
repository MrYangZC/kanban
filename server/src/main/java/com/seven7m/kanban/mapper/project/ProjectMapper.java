package com.seven7m.kanban.mapper.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.dto.project.ProjectStats;
import com.seven7m.kanban.entity.project.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 根据项目 ID 列表一次性聚合统计：成员数、任务数、附件数。
     * 使用 LEFT JOIN + 子查询，避免 N+1 查询。
     */
    @Select("""
            <script>
            SELECT
                p.id AS project_id,
                COALESCE(m.member_count, 0) AS member_count,
                COALESCE(i.issue_count, 0) AS issue_count,
                COALESCE(a.attachment_count, 0) AS attachment_count
            FROM project p
            LEFT JOIN (
                SELECT project_id, COUNT(*) AS member_count
                FROM project_member
                GROUP BY project_id
            ) m ON m.project_id = p.id
            LEFT JOIN (
                SELECT project_id, COUNT(*) AS issue_count
                FROM issue
                WHERE deleted = 0
                GROUP BY project_id
            ) i ON i.project_id = p.id
            LEFT JOIN (
                SELECT i2.project_id, COUNT(*) AS attachment_count
                FROM attachment a
                JOIN issue i2 ON i2.id = a.issue_id
                WHERE i2.deleted = 0
                GROUP BY i2.project_id
            ) a ON a.project_id = p.id
            WHERE p.id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            AND p.deleted = 0
            </script>
            """)
    List<ProjectStats> selectStatsByProjectIds(@Param("ids") List<UUID> ids);
}
