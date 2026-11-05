package com.seven7m.kanban.mapper.issue.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueCommentMapper extends BaseMapper<IssueComment> {

    @Select("""
            SELECT COUNT(*) FROM issue_comment
            WHERE issue_id = #{issueId} AND category = #{category}
            """)
    Long countByIssue(@Param("issueId") UUID issueId, @Param("category") String category);

    /**
     * 批量统计多个工作项的评论数（按 category 隔离）。
     *
     * <p>结果用 {@link CommentCountRow} 承载并显式映射，不再返回 Map（手册 ORM 规约）。
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
    @Results(id = "commentCountRow", value = {
            @Result(column = "issue_id", property = "issueId", id = true),
            @Result(column = "cnt", property = "cnt")
    })
    List<CommentCountRow> countByIssueIds(@Param("issueIds") Collection<UUID> issueIds,
                                          @Param("category") String category);
}
