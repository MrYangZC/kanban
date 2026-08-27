package com.seven7m.kanban.mapper.issue.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface IssueCommentMapper extends BaseMapper<IssueComment> {

    @Select("""
            SELECT COUNT(*) FROM issue_comment
            WHERE issue_id = #{issueId} AND category = #{category}
            """)
    Long countByIssue(@Param("issueId") UUID issueId, @Param("category") String category);

    /**
     * 批量统计多个工作项的评论数（按 category 隔离），返回 [{issue_id, cnt}]。
     */
    @Select("""
            <script>
            SELECT issue_id, COUNT(*) AS cnt FROM issue_comment
            WHERE category = #{category} AND issue_id IN
            <foreach collection="issueIds" item="iid" open="(" separator="," close=")">
                #{iid}
            </foreach>

            GROUP BY issue_id
            </script>
            """)
    List<Map<String, Object>> countByIssueIds(@Param("issueIds") Collection<UUID> issueIds, @Param("category") String category);
}
