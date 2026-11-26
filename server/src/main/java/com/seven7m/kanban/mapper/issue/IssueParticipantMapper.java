package com.seven7m.kanban.mapper.issue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.IssueParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 问题参与者关联 Mapper。批量查询/删除直接使用 BaseMapper + LambdaQueryWrapper。
 */
@Mapper
public interface IssueParticipantMapper extends BaseMapper<IssueParticipant> {

    /**
     * 「共享给我」筛选用：某用户在某项目、某类别下参与的工作项 id。
     *
     * <p>此前工作项列表在条件构造器里手写
     * {@code id IN (SELECT issue_id FROM issue_participant WHERE user_id = ... AND category = ...)}，
     * 把子查询留在了 Service 层。收回 mapper 后要注意 <b>必须带 project_id</b>：
     * 只按 user/category 取会把该用户在所有项目下的工作项 id 全拉回 Java 再拼 {@code IN}，
     * 大账号下既浪费内存也逼近 PostgreSQL 的 65535 个绑定参数上限。
     *
     * <p>{@code issue_participant} 没有 project_id 列，因此 JOIN 三张工作项表的公共集合取项目归属；
     * {@code project_id} 条件会被下推到每个 UNION 分支（各表都有 {@code (project_id, ...)} 索引）。
     */
    @Select("""
            SELECT ip.issue_id
            FROM issue_participant ip
            JOIN (
                SELECT id, project_id FROM task
                UNION ALL
                SELECT id, project_id FROM requirement
                UNION ALL
                SELECT id, project_id FROM bug
            ) w ON w.id = ip.issue_id
            WHERE ip.user_id = #{userId}
              AND ip.category = #{category}
              AND w.project_id = #{projectId}
            """)
    List<UUID> selectIssueIdsByUserInProject(@Param("userId") UUID userId,
                                             @Param("category") String category,
                                             @Param("projectId") UUID projectId);
}
