package com.seven7m.kanban.mapper.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.dto.project.ProjectStats;
import com.seven7m.kanban.entity.project.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 根据项目 ID 列表一次性聚合统计：成员数、工作项数、附件数。
     * 使用 LEFT JOIN + 子查询，避免 N+1 查询。
     * 工作项/附件按 task/requirement/bug 三表 UNION ALL 聚合（V25 拆表后旧 issue 表不再使用）。
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
                FROM (
                    SELECT project_id FROM task
                    UNION ALL
                    SELECT project_id FROM requirement
                    UNION ALL
                    SELECT project_id FROM bug
                ) w
                GROUP BY project_id
            ) i ON i.project_id = p.id
            LEFT JOIN (
                SELECT w.project_id, COUNT(*) AS attachment_count
                FROM attachment a
                JOIN (
                    SELECT id, project_id FROM task
                    UNION ALL
                    SELECT id, project_id FROM requirement
                    UNION ALL
                    SELECT id, project_id FROM bug
                ) w ON w.id = a.issue_id
                GROUP BY w.project_id
            ) a ON a.project_id = p.id
            WHERE p.id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>

            </script>
            """)
    List<ProjectStats> selectStatsByProjectIds(@Param("ids") List<UUID> ids);

    // ==================== 可见性（EXISTS 半连接，一条 SQL 出结果） ====================

    /**
     * 当前用户在某分类下可见的项目（不分页）。
     *
     * <p>可见性条件（成员 / 角色 / 收藏 / 关键词）由 {@link ProjectVisibilitySqlProvider} 用 EXISTS 表达：
     * 早先的实现要么把 {@code IN (SELECT ...)} 写在条件构造器里（SQL 落回服务层），
     * 要么先把项目 id 全捞回 JVM 再拼 {@code IN}（行数多时逼近驱动参数上限）。
     *
     * <p>排序：{@code sortField} 白名单（createdAt/updatedAt/name/projectKey），未知值回落创建时间；
     * {@code sortOrder} 仅 {@code asc} 为升序，其余按"最新在前"。
     */
    @SelectProvider(type = ProjectVisibilitySqlProvider.class, method = "selectVisible")
    List<Project> selectVisible(@Param("q") ProjectVisibilityQuery query);

    /**
     * 同上，分页版：MyBatis-Plus 见到 {@link com.baomidou.mybatisplus.core.metadata.IPage} 参数才会追加
     * LIMIT/OFFSET，所以本方法与 {@link #selectVisible} 共用同一段 SQL。
     */
    @SelectProvider(type = ProjectVisibilitySqlProvider.class, method = "selectVisible")
    List<Project> selectVisiblePage(com.baomidou.mybatisplus.core.metadata.IPage<Project> page,
                                    @Param("q") ProjectVisibilityQuery query);

    /** 可见性计数（分类计数与分页 total 用），SQL 与 {@link #selectVisible} 的 where 完全同源 */
    @SelectProvider(type = ProjectVisibilitySqlProvider.class, method = "countVisible")
    long countVisible(@Param("q") ProjectVisibilityQuery query);
}