package com.seven7m.kanban.entity.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 站内通知。
 *
 * <p>必须同时存 issue_id 与 category：工作项分 task / requirement / bug 三张物理表，
 * 前端要靠 category 才能决定跳转路由（缺陷走独立详情页，其余走看板抽屉）。
 */
@Getter
@Setter
@TableName("notification")
public class Notification {

    /** 同 ProjectHandoff：UUID 主键必须显式赋值，ASSIGN_UUID 会生成 String 导致类型不匹配 */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /** 接收人 */
    private UUID userId;

    /** 通知类型，当前仅 HANDOFF */
    private String type;

    private String title;

    private String body;

    private UUID projectId;

    private UUID issueId;

    /** 工作项大类，决定前端跳转路由 */
    private String category;

    /** 为空表示未读 */
    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
