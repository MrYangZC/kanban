package com.seven7m.kanban.dto.issue;

import lombok.Data;

/**
 * 新增附件请求（兼容旧客户端：仅登记附件元信息）。
 */
@Data
public class AddAttachmentRequest {

    /** 文件名 */
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;
}
