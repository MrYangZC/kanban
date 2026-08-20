package com.seven7m.kanban.mapper.project;

import com.seven7m.kanban.dto.project.ProjectActivityVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ProjectActivityMapper {

    @Select("""
            <script>
            SELECT a.id, a.actor_id, u.display_name AS actor_name, a.action,
                   a.issue_id, i.issue_number, i.title AS issue_title,
                   a.old_value, a.new_value, a.created_at
            FROM issue_activity a
            INNER JOIN issue i ON a.issue_id = i.id AND i.deleted = 0
            LEFT JOIN sys_user u ON a.actor_id = u.id
            WHERE i.project_id = #{projectId}
            ORDER BY a.created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<ProjectActivityVO> selectByProject(@Param("projectId") UUID projectId,
                                            @Param("limit") int limit);
}