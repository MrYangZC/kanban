package com.seven7m.kanban.issue.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class IssueVO {

    private UUID id;
    private UUID projectId;
    private Long issueNumber;
    private String issueKey;
    private UUID issueTypeId;
    private UUID statusId;
    private UUID sprintId;
    private String title;
    private String description;
    private String priority;
    private UUID reporterId;
    private UUID assigneeId;
    private String sortKey;
    private String severity;
    private String module;
    private String fixVersion;
    private String source;
    private Integer archived;
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 缺陷详情页（云效风格）扩展字段
    private UUID verifierId;
    private String iteration;
    private String participants;
    private String ccUsers;
    private LocalDateTime planStart;
    private String noFixReason;

    // 关联展示字段（看板/卡片用）
    private String issueTypeCode;
    private String issueTypeIcon;
    private String issueTypeName;
    private String assigneeName;
    private String reporterName;
    private String verifierName;
    private String statusCode;
    private List<LabelVO> labels = new ArrayList<>();
    private Long commentCount;
}
