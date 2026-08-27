package com.seven7m.kanban.entity.issue;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.config.handler.JsonbTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("issue_activity")
public class IssueActivity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID issueId;
    /** 隔离维度：TASK / REQUIREMENT / BUG，防止跨表串数据 */
    private String category;
    private UUID actorId;
    private String action;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String oldValue;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String newValue;

    private LocalDateTime createdAt;
}
