package com.seven7m.kanban.entity.workitem;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.seven7m.kanban.common.entity.BaseEntity;
import com.seven7m.kanban.common.enums.ItemCategory;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 工作项抽象基类：task / requirement / bug 三张表的通用字段。
 * 子类提供真实表名（@TableName）与 category() 实现，保证物理隔离。
 */
@Getter
@Setter
public abstract class AbstractWorkItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    /** 项目内序号，如 "1001" */
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
    private LocalDate dueDate;
    private String sortKey;
    private Boolean archived;
    /**
     * 首次进入"已完成"类别状态的时间。
     * 用 updated_at 代替会把"任何一次编辑"都算进解决周期（改个标题就变长），
     * 因此单独记录；离开已完成类别时清空。
     */
    private java.time.LocalDateTime doneAt;

    /** 子类返回所属物理表对应的类别，用于路由与子表隔离。 */
    public abstract ItemCategory category();
}
