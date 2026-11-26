package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 更新工作项请求（部分更新语义：null 表示不修改该字段）。
 * 因此只使用 @Size / @Pattern 这类把 null 视为合法的约束，
 * 不能使用 @NotBlank（会把"未传"误判为非法）。
 */
@Data
public class UpdateIssueRequest {

    @Size(max = 500, message = "标题长度不能超过 500")
    private String title;
    private String description;

    @Pattern(regexp = "^P[0-3]$", message = "优先级仅支持 P0~P3")
    private String priority;
    private UUID issueTypeId;
    private UUID assigneeId;
    private UUID verifierId;
    /** 调整父工作项（传 null 表示不改，置空请用 clearFields） */
    private UUID parentId;
    /** 实际完成时间：不传则按状态自动取当前时间（导入历史数据时需要显式指定） */
    private LocalDate doneAt;
    private UUID sprintId;
    private LocalDate dueDate;

    @Pattern(regexp = "^S[1-4]$", message = "严重度仅支持 S1~S4")
    private String severity;

    @Size(max = 100, message = "模块长度不能超过 100")
    private String module;

    @Size(max = 60, message = "修复版本长度不能超过 60")
    private String fixVersion;

    @Size(max = 60, message = "来源长度不能超过 60")
    private String source;
    private Boolean archived;
    private UUID statusId;

    // 缺陷详情页（云效风格）基础字段

    @Size(max = 100, message = "迭代长度不能超过 100")
    private String iteration;
    /** 参与者用户 ID 列表（全量替换 issue_participant） */
    private List<String> participants;
    /** 抄送人用户 ID 列表（全量替换 issue_cc） */
    private List<String> ccUsers;
    private LocalDate planStart;
    private String noFixReason;

    /** 标签 ID 列表（全量替换 issue_label；传空 list 可清空，不传则不处理） */
    private List<UUID> labelIds;

    /**
     * 需要显式置空的字段名集合（如 ["assigneeId","verifierId","dueDate","planStart"]）。
     * 未传或为 null 时保持旧语义：仅更新非 null 字段。
     */
    private Set<String> clearFields;
}
