package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.util.UUID;

/**
 * 新增评论请求。
 */
@Data
public class AddCommentRequest {

    /** 评论内容 */
    private String content;

    /** 父评论 id，顶楼评论为 null */
    private UUID parentId;
}
