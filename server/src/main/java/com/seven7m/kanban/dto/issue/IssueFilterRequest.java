package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class IssueFilterRequest {
    @NotNull
    private UUID projectId;

    /** 左侧范围：ASSIGNED(我负责的)/REPORTED(我创建的)/ARCHIVED(已归档)/ACTIVE(未归档) */
    private String scope;

    private UUID statusId;
    /**
     * 状态类别过滤：TODO / IN_PROGRESS / DONE。
     * 度量页的统计数字要能点进对应列表，而一张卡片对应的是「一整类状态」而不是某个具体节点，
     * 所以这里按 issue_status.category 过滤，而不是按 statusId。
     */
    private String statusCategory;
    /** 只看逾期：未完成且 due_date < 今天。度量页「逾期」数字跳转用 */
    private Boolean overdue;
    /** 迭代（冲刺）过滤：为空表示不过滤。三表均有 sprint_id 列 */
    private UUID sprintId;
    private String priority;
    private String severity;
    private UUID labelId;
    private String keyword;

    /** 工作项类型编码过滤（如 ["STORY","FEATURE"]），为空表示不过滤 */
    private List<String> issueTypeCodes;

    /** 分组维度（可选）：STATUS / ASSIGNEE / PRIORITY / SPRINT。为空时按状态列展示。 */
    private String groupBy;

    /** 分页页码，默认 1（表格视图使用） */
    private Integer page = 1;

    /** 每页数量，默认 20（表格视图使用） */
    private Integer size = 20;

    /** 排序字段，仅允许白名单：updatedAt / priority / severity / statusId / sortKey / createdAt */
    private String sortField;

    /** 排序方向：asc / desc，默认 asc */
    private String sortOrder = "asc";
}
