package com.seven7m.kanban.mapper.workitem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.workitem.Bug;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BugMapper extends BaseMapper<Bug> {

    @Select("SELECT nextval('issue_number_seq')::text")
    String nextIssueNumber();

    @Select("SELECT project_id FROM bug WHERE id = #{id}")
    UUID selectProjectId(@Param("id") UUID id);

    /** 撤销交接的可用性判定用：返回当前负责人，无负责人/不存在返回 null */
    @Select("SELECT assignee_id FROM bug WHERE id = #{id}")
    UUID selectAssigneeId(@Param("id") UUID id);

    @Select("SELECT id FROM bug WHERE project_id = #{projectId}")
    List<UUID> selectIdsByProject(@Param("projectId") UUID projectId);
}
