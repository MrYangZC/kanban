package com.seven7m.kanban.dto.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 站内通知视图。
 *
 * @param category 工作项大类，前端据此决定跳转路由（BUG 走缺陷详情页，其余走看板抽屉）
 */
public record NotificationVO(
        UUID id,
        String type,
        String title,
        String body,
        UUID projectId,
        UUID issueId,
        String category,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
