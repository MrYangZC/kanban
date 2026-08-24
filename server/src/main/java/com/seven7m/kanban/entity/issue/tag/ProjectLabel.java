package com.seven7m.kanban.entity.issue.tag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目选用标签关联表（project_label）。
 * 全局化后 label 为全局标签库，本表表达「项目启用了哪些标签」。
 */
@Getter
@Setter
@TableName("project_label")
public class ProjectLabel {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    private UUID labelId;
    private LocalDateTime createdAt;
}
