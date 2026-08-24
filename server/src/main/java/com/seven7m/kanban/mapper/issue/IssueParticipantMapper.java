package com.seven7m.kanban.mapper.issue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.IssueParticipant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问题参与者关联 Mapper。批量查询/删除直接使用 BaseMapper + LambdaQueryWrapper。
 */
@Mapper
public interface IssueParticipantMapper extends BaseMapper<IssueParticipant> {
}
