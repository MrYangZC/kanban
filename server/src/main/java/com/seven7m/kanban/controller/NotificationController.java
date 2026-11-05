package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.notification.NotificationVO;
import com.seven7m.kanban.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 站内通知。挂在 /users 下是刻意的：这是「我收到的」用户级数据，
 * WebConfig 已把 /users/** 排除出项目权限拦截器，但仍受登录态校验。
 * 所有接口只认 UserContext 里的当前用户，不接受前端传 userId。
 */
@RestController
@RequestMapping("/users/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public R<PageResult<NotificationVO>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return R.ok(notificationService.page(UserContext.getUserId(), page, size));
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(notificationService.unreadCount(UserContext.getUserId()));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable UUID id) {
        notificationService.markRead(UserContext.getUserId(), id);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Integer> markAllRead() {
        return R.ok(notificationService.markAllRead(UserContext.getUserId()));
    }
}
