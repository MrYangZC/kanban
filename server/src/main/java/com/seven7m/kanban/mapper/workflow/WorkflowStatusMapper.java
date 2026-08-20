package com.seven7m.kanban.mapper.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.workflow.WorkflowStatus;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowStatusMapper extends BaseMapper<WorkflowStatus> {
}
