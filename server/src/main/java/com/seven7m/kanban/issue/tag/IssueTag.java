package com.seven7m.kanban.issue.tag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("label")
public class IssueTag {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID projectId;
    private String name;
    private String color;
    private LocalDateTime createdAt;
}
