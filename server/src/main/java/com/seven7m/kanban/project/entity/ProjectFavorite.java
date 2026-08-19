package com.seven7m.kanban.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目收藏关系实体。
 * 记录用户对项目的收藏关系，用于"我收藏的"分类筛选。
 */
@Getter
@Setter
@TableName("project_favorite")
public class ProjectFavorite {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    private UUID userId;
    private LocalDateTime createdAt;
}