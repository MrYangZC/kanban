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

    /**
     * 当前用户在项目成员名册（project_member）中的项目 id 集合。
     *
     * <p>此前这段子查询以 {@code apply("id IN (SELECT project_id FROM project_member ...)")}
     * 的形式写在 {@code ProjectQueryScope} 与 {@code ProjectService#getCategoryCounts} 里：
     * SQL 文本散落在 Java 业务代码里，改口径要去两处找字符串，也没法单独 review/加索引。
     * 现在 SQL 只在这里出现一次，服务层只做 {@code in(Project::getId, ids)}。
     *
     * <p>返回值可能为空集合：调用方必须先判空再拼 {@code IN}（PostgreSQL 的 {@code IN ()} 是语法错误）。
     */
    @Select("""
            SELECT DISTINCT pm.project_id
            FROM project_member pm
            WHERE pm.user_id = #{userId}
            """)
    List<UUID> selectMemberProjectIds(@Param("userId") UUID userId);

    /**
     * 当前用户在指定项目中持有 {@code codes} 之一角色码的项目 id 集合（"我管理的"分类）。
     *
     * <p>角色码由调用方以 {@link com.seven7m.kanban.entity.project.ProjectRole} 常量清单传入，
     * <b>刻意不把 'OWNER','ADMIN','PM' 写死在注解里</b>：哪些角色算"可管理"是业务口径，
     * 写死进 SQL 后改口径要回到注解字符串里翻，且容易与接口注解上的 project:member 漂移。
     *
     * <p>{@code codes} 不能为空（空清单会拼出 {@code IN ()}）。
     */
    @Select("""
            <script>
            SELECT DISTINCT ur.project_id
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.code IN
              <foreach collection="codes" item="code" open="(" separator="," close=")">
                  #{code}
              </foreach>
            </script>
            """)
    List<UUID> selectManagedProjectIds(@Param("userId") UUID userId, @Param("codes") List<String> codes);

    /**
     * 当前用户收藏（project_favorite）的项目 id 集合。
     *
     * <p>同 {@link #selectMemberProjectIds}：原先是 {@code ProjectQueryScope} 里的子查询字符串。
     */
    @Select("""
            SELECT DISTINCT pf.project_id
            FROM project_favorite pf
            WHERE pf.user_id = #{userId}
            """)
    List<UUID> selectFavoritedProjectIds(@Param("userId") UUID userId);
}
