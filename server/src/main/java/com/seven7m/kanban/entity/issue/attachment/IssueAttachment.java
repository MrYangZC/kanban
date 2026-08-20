package com.seven7m.kanban.entity.issue.attachment;

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
    /** 附件可访问地址（/api/uploads/...），存量数据为 NULL */
    private String url;
    private UUID uploadedBy;
    private LocalDateTime createdAt;
}
