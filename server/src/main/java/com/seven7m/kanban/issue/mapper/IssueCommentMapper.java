package com.seven7m.kanban.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.issue.entity.IssueComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IssueCommentMapper extends BaseMapper<IssueComment> {
}
