package com.seven7m.kanban.mapper.issue.tag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.tag.IssueTag;
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
            WHERE il.issue_id = #{issueId} AND il.category = #{category}
            ORDER BY l.name
            """)
    List<IssueTag> selectByIssue(@Param("issueId") UUID issueId, @Param("category") String category);

    /**
     * 批量查询多个工作项的标签（按 category 隔离）。
     */
    @Select("""
            <script>
            SELECT il.issue_id, l.* FROM label l
            JOIN issue_label il ON il.label_id = l.id
            WHERE il.category = #{category} AND il.issue_id IN
            <foreach collection="issueIds" item="iid" open="(" separator="," close=")">
                #{iid}
            </foreach>
            ORDER BY l.name
            </script>
            """)
    List<IssueTag> selectByIssueIds(@Param("issueIds") Collection<UUID> issueIds, @Param("category") String category);

    @Select("""
            SELECT il.issue_id FROM issue_label il
            JOIN label l ON l.id = il.label_id
            WHERE il.category = #{category} AND il.label_id IN
            <foreach collection="labelIds" item="lid" open="(" separator="," close=")">
                #{lid}
            </foreach>
            """)
    List<UUID> selectIssueIdsByLabels(@Param("labelIds") Collection<UUID> labelIds, @Param("category") String category);

    /**
     * 按工作项 id 集合 + category 批量删除标签关联（级联清理用）。
     */
    @Delete("""
            <script>
            DELETE FROM issue_label WHERE category = #{category} AND issue_id IN
            <foreach collection="issueIds" item="iid" open="(" separator="," close=")">
                #{iid}
            </foreach>
            </script>
            """)
    void deleteIssueLabelByIssueIdsAndCategory(@Param("issueIds") Collection<UUID> issueIds, @Param("category") String category);

    @Select("""
            SELECT l.* FROM label l
            JOIN project_label pl ON pl.label_id = l.id
            WHERE pl.project_id = #{projectId}
            ORDER BY l.name
            """)
    List<IssueTag> selectByProject(@Param("projectId") UUID projectId);

    @Insert("INSERT INTO issue_label (issue_id, label_id, category) VALUES (#{issueId}, #{labelId}, #{category})")
    void insertIssueLabel(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId, @Param("category") String category);

    @Delete("DELETE FROM issue_label WHERE issue_id = #{issueId} AND label_id = #{labelId} AND category = #{category}")
    void deleteIssueLabel(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId, @Param("category") String category);

    @Select("""
            SELECT COUNT(*) FROM issue_label
            WHERE issue_id = #{issueId} AND label_id = #{labelId} AND category = #{category}
            """)
    Integer countIssueLabel(@Param("issueId") UUID issueId, @Param("labelId") UUID labelId, @Param("category") String category);
}
