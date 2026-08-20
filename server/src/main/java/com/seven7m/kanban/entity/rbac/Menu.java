package com.seven7m.kanban.entity.rbac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 菜单 / 权限点。前端菜单树与后端接口鉴权共用。
 */
@Getter
@Setter
@TableName("menu")
public class Menu extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 权限点编码，如 ISSUE_CREATE */
    private String code;

    private String name;

    private UUID parentId;

    /** MENU / BUTTON */
    private String type;

    private Integer sortOrder;
}
