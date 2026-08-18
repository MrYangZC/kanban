package com.seven7m.kanban.workspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("workspace_member")
public class WorkspaceMember {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID workspaceId;
    private UUID userId;
    private String role;
    private LocalDateTime joinedAt;
}
