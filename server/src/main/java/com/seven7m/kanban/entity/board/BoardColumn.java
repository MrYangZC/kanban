package com.seven7m.kanban.entity.board;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("board_column")
public class BoardColumn {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID boardId;
    private UUID statusId;
    private Integer sortOrder;
    private Integer wipLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
