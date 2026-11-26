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

    /**
     * 目标状态列当前的列尾 sort_key；空列返回 null。
     *
     * <p>只回一列一行：此前服务层用 selectOne(...).last("LIMIT 1") 把 SQL 片段写进了条件构造器，
     * 为了不写 SQL 又改成「取整列再取第一条」，在列内卡片很多时会把整列实体（含正文列）都查回来。
     * 取一条的语义本来就属于 SQL，放回 Mapper 后用 {@code LIMIT 1} 表达最直接。
     */
    @Select("SELECT sort_key FROM task WHERE project_id = #{projectId} AND status_id = #{statusId} "
            + "ORDER BY sort_key DESC LIMIT 1")
    String selectTailSortKey(@Param("projectId") UUID projectId, @Param("statusId") UUID statusId);
}