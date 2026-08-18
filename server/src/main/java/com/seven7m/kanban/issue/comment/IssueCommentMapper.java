package com.seven7m.kanban.issue.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueCommentMapper extends BaseMapper<IssueComment> {

    @Select("""
            SELECT COUNT(*) FROM issue_comment
            WHERE issue_id = #{issueId} AND deleted = 0
            """)
    Long countByIssue(@Param("issueId") UUID issueId);
}
