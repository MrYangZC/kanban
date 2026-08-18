package com.seven7m.kanban.board.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.board.entity.BoardColumn;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BoardColumnMapper extends BaseMapper<BoardColumn> {
}
