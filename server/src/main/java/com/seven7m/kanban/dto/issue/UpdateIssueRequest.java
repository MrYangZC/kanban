package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
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
    private LocalDate dueDate;
    private String severity;
    private String module;
    private String fixVersion;
    private String source;
    private Boolean archived;
    private UUID statusId;

    // 缺陷详情页（云效风格）基础字段
    private String iteration;
    /** 参与者用户 ID 列表（全量替换 issue_participant） */
    private List<String> participants;
    /** 抄送人用户 ID 列表（全量替换 issue_cc） */
    private List<String> ccUsers;
    private LocalDate planStart;
    private String noFixReason;

    /**
     * 需要显式置空的字段名集合（如 ["assigneeId","verifierId","dueDate","planStart"]）。
     * 未传或为 null 时保持旧语义：仅更新非 null 字段。
     */
    private Set<String> clearFields;
}
