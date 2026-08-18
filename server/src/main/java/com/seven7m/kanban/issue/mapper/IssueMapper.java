package com.seven7m.kanban.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.issue.entity.Issue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {

    /**
     * 一次拉取项目全量未删除 Issue（看板聚合用，内存按 status_id 分组）。
     */
    @Select("""
            SELECT * FROM issue
            WHERE project_id = #{projectId}
              AND deleted = 0
            ORDER BY status_id, sort_key
            """)
    List<Issue> selectByProjectForBoard(@Param("projectId") UUID projectId);

    /**
     * 从全局序列取下一个 issue_number，避免应用层 selectCount+1 的并发重复。
     */
    @Select("SELECT nextval('issue_number_seq')")
    long nextIssueNumber();
}
