package com.seven7m.kanban.sprint.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@TableName("sprint")
public class Sprint extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    private String name;
    private String goal;
    /** 迭代状态：PLANNED / ACTIVE / COMPLETED */
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
}
