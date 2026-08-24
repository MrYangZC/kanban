package com.seven7m.kanban.entity.rbac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 菜单（导航）。仅承载前端导航树职责；按钮级权限已收敛至 permission 表，
 * 由 role_permission 承载。历史 BUTTON 节点已随 V26 迁移删除。
 */
@Getter
@Setter
@TableName("menu")
public class Menu extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 菜单编码，仅作导航标识 */
    private String code;

    private String name;

    private UUID parentId;

    /** 菜单类型，当前仅 MENU（保留字段以兼容历史数据） */
    private String type;

    private Integer sortOrder;
}
