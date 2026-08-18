package com.seven7m.kanban.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@TableName("project")
public class Project extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID workspaceId;
    private String projectKey;
    private String name;
    private String description;
    private String avatarUrl;
    private String status;
    private UUID leadUserId;
    private UUID createdBy;
}
