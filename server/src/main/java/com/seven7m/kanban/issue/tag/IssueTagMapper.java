package com.seven7m.kanban.issue.tag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueTagMapper extends BaseMapper<IssueTag> {

    @Select("""
            SELECT l.* FROM label l
            JOIN issue_label il ON il.label_id = l.id
            WHERE il.issue_id = #{issueId}
            ORDER BY l.name
            """)
    List<IssueTag> selectByIssue(@Param("issueId") UUID issueId);

    @Select("""
            SELECT l.* FROM label l
            WHERE l.project_id = #{projectId}
            ORDER BY l.name
            """)
    List<IssueTag> selectByProject(@Param("projectId") UUID projectId);

    @Insert("INSERT INTO issue_label (issue_id, label_id) VALUES (#{issueId}, #{labelId})")
    void insertIssueLabel(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId);

    @Delete("DELETE FROM issue_label WHERE issue_id = #{issueId} AND label_id = #{labelId}")
    void deleteIssueLabel(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId);

    @Select("""
            SELECT COUNT(*) FROM issue_label
            WHERE issue_id = #{issueId} AND label_id = #{labelId}
            """)
    Integer countIssueLabel(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId);
}
