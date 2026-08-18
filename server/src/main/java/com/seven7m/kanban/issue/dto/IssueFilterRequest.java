package com.seven7m.kanban.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class IssueFilterRequest {
    @NotNull
    private UUID projectId;

    /** 左侧范围：ASSIGNED(我负责的)/REPORTED(我创建的)/ARCHIVED(已归档)/ACTIVE(未归档) */
    private String scope;

    private UUID statusId;
    private String priority;
    private String severity;
    private UUID labelId;
    private String keyword;

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
