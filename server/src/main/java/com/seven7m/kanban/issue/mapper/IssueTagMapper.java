package com.seven7m.kanban.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.issue.entity.IssueTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueTagMapper extends BaseMapper<IssueTag> {

    @Select("""
            SELECT l.*, il.issue_id AS issue_id
            FROM label l
            JOIN issue_label il ON il.label_id = l.id
            WHERE il.issue_id = #{issueId}
            """)
    List<IssueTag> selectByIssueId(UUID issueId);
}
