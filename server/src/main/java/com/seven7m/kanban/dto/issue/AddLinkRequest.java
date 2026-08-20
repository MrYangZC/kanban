package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.util.UUID;

/**
 * 新增 issue 关联请求。
 */
@Data
public class AddLinkRequest {

    /** 目标 issue id */
    private UUID targetIssueId;

    /** 关联类型 */
    private String relationType;
}
