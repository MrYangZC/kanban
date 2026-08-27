package com.seven7m.kanban.mapper.workitem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.workitem.Requirement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface RequirementMapper extends BaseMapper<Requirement> {

    @Select("""
            SELECT * FROM requirement
            WHERE project_id = #{projectId}
            ORDER BY status_id, sort_key
            """)
    List<Requirement> selectByProjectForBoard(@Param("projectId") UUID projectId);

    @Select("SELECT nextval('issue_number_seq')::text")
    String nextIssueNumber();

    @Select("SELECT project_id FROM requirement WHERE id = #{id}")
    UUID selectProjectId(@Param("id") UUID id);

    @Select("SELECT id FROM requirement WHERE project_id = #{projectId}")
    List<UUID> selectIdsByProject(@Param("projectId") UUID projectId);
}
