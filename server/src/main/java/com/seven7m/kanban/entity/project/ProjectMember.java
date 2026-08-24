package com.seven7m.kanban.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目成员名册。仅记录用户在某项目中的成员身份，不承载角色授权；
 * 角色授权由 {@code user_role} 表承载。
 */
@Getter
@Setter
@TableName("project_member")
public class ProjectMember {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    private UUID userId;
    private LocalDateTime createdAt;
}
