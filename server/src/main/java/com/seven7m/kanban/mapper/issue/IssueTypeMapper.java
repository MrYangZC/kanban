package com.seven7m.kanban.mapper.issue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.IssueType;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IssueTypeMapper extends BaseMapper<IssueType> {
}
