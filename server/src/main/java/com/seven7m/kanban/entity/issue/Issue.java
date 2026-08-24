package com.seven7m.kanban.entity.issue;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("issue")
public class Issue extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    /** 问题编号：字符串类型（全局序号），如 "1001" */
    private String issueNumber;
    private UUID parentId;
    private UUID issueTypeId;
    private UUID statusId;
    private String title;
    private String description;
    private String priority;
    private UUID reporterId;
    private UUID assigneeId;
    private UUID verifierId;
    private UUID sprintId;
    private BigDecimal estimate;
    private LocalDateTime dueDate;
    private String sortKey;
    private String severity;
    private String module;
    private String fixVersion;
    private String source;
    private Integer archived;
    private UUID createdBy;

    // 缺陷详情页（云效风格）扩展字段
    private String iteration;
    /** 计划开始时间 */
    private LocalDateTime planStart;
    /** 不修复理由 */
    private String noFixReason;
}
