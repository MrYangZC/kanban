package com.seven7m.kanban.sprint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.sprint.entity.Sprint;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SprintMapper extends BaseMapper<Sprint> {
}
