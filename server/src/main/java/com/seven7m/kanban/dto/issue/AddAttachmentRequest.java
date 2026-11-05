package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增附件请求（兼容旧客户端：仅登记附件元信息）。
 *
 * <p>长度上限与 `attachment` 表列宽一致；校验由控制器 `@Valid` 触发。
 */
@Data
public class AddAttachmentRequest {

    /** 文件名，必填（列宽 varchar(255)） */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;

    /** MIME 类型 */
    @Size(max = 100, message = "MIME 类型长度不能超过 100")
    private String contentType;

    /** 文件大小（字节），不可为负 */
    @Min(value = 0, message = "文件大小不能为负数")
    private Long fileSize;
}
