package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class IssueVO {

    private UUID id;
    private UUID projectId;
    /**
     * 上级工作项 id（层级树用）。
     *
     * <p>此前 VO 里没有这个字段，前端拿到的永远是「扁平列表」：树形视图把所有行当根节点渲染
     * （238 条数据只显示出 3 条就是这个问题），表格视图也无法缩进。实体上有 parentId，
     * 这里补上后由 {@code IssueVoAssembler} 的 BeanUtils 拷贝自动带出。
     */
    private UUID parentId;
    private String issueNumber;
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
    private Boolean archived;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 实际完成时间：状态进入「已完成」类别时落值，导入历史数据时可显式指定 */
    private LocalDateTime doneAt;

    // 缺陷详情页（云效风格）扩展字段
    private UUID verifierId;
    private String iteration;
    /** 参与者用户 ID 列表（来自 issue_participant 关联表） */
    private List<String> participants;
    /** 抄送人用户 ID 列表（来自 issue_cc 关联表） */
    private List<String> ccUsers;
    private LocalDate planStart;
    private String noFixReason;

    // 关联展示字段（看板/卡片用）
    private String issueTypeCode;
    private String issueTypeIcon;
    private String issueTypeName;
    private String assigneeName;
    private String reporterName;
    private String verifierName;
    /** 对应人员的头像地址，列表/卡片人员列据此显示真实头像 */
    private String assigneeAvatarUrl;
    private String reporterAvatarUrl;
    private String verifierAvatarUrl;
    private String statusCode;
    private List<LabelVO> labels = new ArrayList<>();
    private Long commentCount;
}
