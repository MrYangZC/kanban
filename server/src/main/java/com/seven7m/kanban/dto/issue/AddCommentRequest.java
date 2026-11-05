package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 新增评论请求。
 *
 * <p>校验放在 DTO 上、由控制器 `@Valid` 触发（手册安全规约：用户请求传入的任何参数必须做有效性验证）。
 */
@Data
public class AddCommentRequest {

    /** 评论内容，必填；上限与前端编辑器限制一致 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 20000, message = "评论内容不能超过 20000 字")
    private String content;

    /** 父评论 id，顶楼评论为 null */
    private UUID parentId;
}
