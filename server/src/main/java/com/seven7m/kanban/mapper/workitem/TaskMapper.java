package com.seven7m.kanban.mapper.workitem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.workitem.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /** 一次拉取项目全量 task（看板聚合用），只看板列内排序 */
    @Select("""
            SELECT * FROM task
            WHERE project_id = #{projectId}
            ORDER BY status_id, sort_key
            """)
    List<Task> selectByProjectForBoard(@Param("projectId") UUID projectId);

    /** 全局序列取下一个 issue_number（字符串） */
    @Select("SELECT nextval('issue_number_seq')::text")
    String nextIssueNumber();

    /** 路由用：返回该工作项所属项目 id，不存在返回 null */
    @Select("SELECT project_id FROM task WHERE id = #{id}")
    UUID selectProjectId(@Param("id") UUID id);

    /** 撤销交接的可用性判定用：返回当前负责人，无负责人/不存在返回 null */
    @Select("SELECT assignee_id FROM task WHERE id = #{id}")
    UUID selectAssigneeId(@Param("id") UUID id);

    /** 返回该项目下的全部工作项 id */
    @Select("SELECT id FROM task WHERE project_id = #{projectId}")
    List<UUID> selectIdsByProject(@Param("projectId") UUID projectId);
}
