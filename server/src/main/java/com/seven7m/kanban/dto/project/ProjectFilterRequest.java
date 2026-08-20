package com.seven7m.kanban.dto.project;

import lombok.Data;

@Data
public class ProjectFilterRequest {

    /** 分类筛选：all / managed / participated / favorited / archived */
    private String category;

    /** 关键词：按项目名称、projectKey 模糊匹配 */
    private String keyword;

    /** 分页页码，默认 1 */
    private Integer page = 1;

    /** 每页数量，默认 10，上限 100 */
    private Integer size = 10;

    /** 排序字段白名单：createdAt / updatedAt / name / projectKey */
    private String sortField;

    /** 排序方向：asc / desc，默认 desc */
    private String sortOrder = "desc";
}