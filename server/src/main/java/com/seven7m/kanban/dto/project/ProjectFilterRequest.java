package com.seven7m.kanban.dto.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 项目列表筛选请求。
 *
 * <p>按手册要求：POJO 不设属性默认值（分页缺省值由 Service 处理），参数有效性由控制器
 * `@Valid` 触发校验，而不是依赖下游 clamp。
 */
@Data
public class ProjectFilterRequest {

    /** 分类筛选：all / managed / participated / favorited / archived */
    @Size(max = 20, message = "分类长度不能超过 20")
    private String category;

    /** 关键词：按项目名称、projectKey 模糊匹配 */
    @Size(max = 100, message = "关键词长度不能超过 100")
    private String keyword;

    /** 分页页码；为空按 1 处理 */
    @Min(value = 1, message = "页码最小为 1")
    private Integer page;

    /** 每页数量；为空按 10 处理，上限 100 */
    @Min(value = 1, message = "每页数量最小为 1")
    @Max(value = 100, message = "每页数量最大为 100")
    private Integer size;

    /** 排序字段白名单：createdAt / updatedAt / name / projectKey */
    @Size(max = 20, message = "排序字段长度不能超过 20")
    private String sortField;

    /** 排序方向：asc / desc，缺省 desc */
    @Size(max = 4, message = "排序方向长度不能超过 4")
    private String sortOrder;
}
