package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class UpdateIssueRequest {

    private String title;
    private String description;
    private String priority;
    private UUID issueTypeId;
    private UUID assigneeId;
    private UUID verifierId;
    private UUID sprintId;
    private LocalDateTime dueDate;
    private String severity;
    private String module;
    private String fixVersion;
    private String source;
    private Integer archived;
    private UUID statusId;

    // 缺陷详情页（云效风格）基础字段
    private String iteration;
    /** JSON 数组字符串，如 ["uuid1","uuid2"] */
    private String participants;
    /** JSON 数组字符串，如 ["uuid1","uuid2"] */
    private String ccUsers;
    private LocalDateTime planStart;
    private String noFixReason;

    /**
     * 需要显式置空的字段名集合（如 ["assigneeId","verifierId","dueDate","planStart"]）。
     * 未传或为 null 时保持旧语义：仅更新非 null 字段。
     */
    private Set<String> clearFields;
}
