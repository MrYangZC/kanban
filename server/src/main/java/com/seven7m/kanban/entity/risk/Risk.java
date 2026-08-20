package com.seven7m.kanban.entity.risk;

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
@TableName("risk")
public class Risk extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    private String title;
    private String description;
    /** 风险级别：LOW / MEDIUM / HIGH / CRITICAL */
    private String level;
    /** 风险状态：OPEN / MITIGATING / CLOSED */
    private String status;
    private UUID ownerId;
    private LocalDate dueDate;
}
