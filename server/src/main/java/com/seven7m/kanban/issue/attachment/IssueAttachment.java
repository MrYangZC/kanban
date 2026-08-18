package com.seven7m.kanban.issue.attachment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("attachment")
public class IssueAttachment {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID issueId;
    private String fileName;
    private String storageKey;
    private String contentType;
    private Long fileSize;
    private UUID uploadedBy;
    private LocalDateTime createdAt;
}
