package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.util.UUID;

/**
 * 新增标签请求。
 */
@Data
public class AddTagRequest {

    /** 所属项目 id */
    private UUID projectId;

    /** 标签名 */
    private String name;

    /** 标签颜色 */
    private String color;
}
