package com.seven7m.kanban.workspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@TableName("workspace")
public class Workspace extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String name;
    private String workspaceKey;
    private String description;
    private String avatarUrl;
    private UUID createdBy;
}
