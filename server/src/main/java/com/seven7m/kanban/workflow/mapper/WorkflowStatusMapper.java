package com.seven7m.kanban.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.workflow.entity.WorkflowStatus;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowStatusMapper extends BaseMapper<WorkflowStatus> {
}
