package com.seven7m.kanban.issue.tag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;

import java.util.Collection;
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

    /**
     * 批量查询多个 Issue 的标签，返回行中 issue_id 通过 IssueTag.issueId 承载。
     */
    @Select("""
            <script>
            SELECT il.issue_id, l.* FROM label l
            JOIN issue_label il ON il.label_id = l.id
            WHERE il.issue_id IN
            <foreach collection="issueIds" item="iid" open="(" separator="," close=")">
                #{iid}
            </foreach>
            ORDER BY l.name
            </script>
            """)
    List<IssueTag> selectByIssueIds(@Param("issueIds") Collection<UUID> issueIds);

    /**
     * 按 Issue id 集合批量删除标签关联（级联清理用，物理删除）。
     */
    @Delete("""
            <script>
            DELETE FROM issue_label WHERE issue_id IN
            <foreach collection="issueIds" item="iid" open="(" separator="," close=")">
                #{iid}
            </foreach>
            </script>
            """)
    void deleteIssueLabelByIssueIds(@Param("issueIds") Collection<UUID> issueIds);

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
