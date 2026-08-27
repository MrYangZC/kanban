package com.seven7m.kanban.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 带审计字段的实体基类。
 */
@Getter
@Setter
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private UUID createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private UUID updatedBy;
}
