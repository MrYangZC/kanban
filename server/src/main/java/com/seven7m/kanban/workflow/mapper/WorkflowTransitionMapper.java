package com.seven7m.kanban.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.workflow.entity.WorkflowTransition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowTransitionMapper extends BaseMapper<WorkflowTransition> {
}
