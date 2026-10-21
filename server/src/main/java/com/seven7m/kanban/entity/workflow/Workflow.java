package com.seven7m.kanban.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("workflow")
public class Workflow {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String name;
    private String description;
    private Boolean isDefault;

    /** 适用工作项大类：TASK / REQUIREMENT / BUG（每类一条默认模板，见 V31） */
    private String appliesTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
