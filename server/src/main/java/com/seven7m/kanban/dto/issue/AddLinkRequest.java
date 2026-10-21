package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 新增 issue 关联请求。relationType 的取值白名单由 IssueLinkService 校验，
 * 此处仅做非空与长度兜底。
 */
@Data
public class AddLinkRequest {

    /** 目标 issue id */
    @NotNull(message = "目标工作项不能为空")
    private UUID targetIssueId;

    /** 关联类型 */
    @NotBlank(message = "关联类型不能为空")
    @Size(max = 32, message = "关联类型长度不能超过 32")
    private String relationType;
}
