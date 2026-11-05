package com.seven7m.kanban.service.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.notification.NotificationVO;
import com.seven7m.kanban.entity.notification.Notification;
import com.seven7m.kanban.mapper.notification.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 站内通知。当前只有交接（HANDOFF）一种来源。
 *
 * <p>项目未配置 MyBatis-Plus 分页插件，这里沿用现有服务的手写 LIMIT/OFFSET 方式，
 * 不要改成 selectPage（会退化成全表返回）。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String TYPE_HANDOFF = "HANDOFF";
    private static final int MAX_PAGE_SIZE = 50;
    /** 与 notification.body 列宽一致 */
    private static final int MAX_BODY = 500;

    private final NotificationMapper notificationMapper;

    /**
     * 交接通知，接收人即新的负责人。
     * 调用方处在状态流转事务内，这里刻意不加 @Transactional（跟随外层事务）。
     *
     * @param landing 交接落点的一句话说明（「已放进迭代「S1」」等）；没配落点时为 null，
     *                正文与没有落点功能时完全一致
     */
    public void notifyHandoff(UUID toUserId, UUID projectId, UUID issueId, String category,
                              String issueNumber, String issueTitle, String statusName, String note,
                              String landing) {
        if (toUserId == null || issueId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(toUserId);
        notification.setType(TYPE_HANDOFF);
        notification.setTitle("收到一条交接工作项");
        notification.setProjectId(projectId);
        notification.setIssueId(issueId);
        notification.setCategory(category);

        String subject = issueNumber == null || issueNumber.isBlank()
                ? safe(issueTitle)
                : issueNumber + " " + safe(issueTitle);
        StringBuilder body = new StringBuilder()
                .append("「").append(subject.trim()).append("」")
                .append("已流转到「").append(safe(statusName)).append("」，请接手");
        if (landing != null && !landing.isBlank()) {
            body.append("。").append(landing.trim());
        }
        if (note != null && !note.isBlank()) {
            body.append("。交接说明：").append(note.trim());
        }
        notification.setBody(truncate(body.toString(), MAX_BODY));
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    /**
     * 交接被撤销：工作项退回原负责人名下。
     *
     * <p>沿用 {@code HANDOFF} 类型而不是新开类型：铃铛跳转、未读计数、已读链路完全复用，
     * 差异只在标题与正文，前端不需要为它加分支。调用方同样处在撤销事务内，这里不加 @Transactional。
     */
    public void notifyHandoffRevoked(UUID toUserId, UUID projectId, UUID issueId, String category,
                                     String issueNumber, String issueTitle, String operatorName) {
        if (toUserId == null || issueId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(toUserId);
        notification.setType(TYPE_HANDOFF);
        notification.setTitle("工作项交接已撤销");
        notification.setProjectId(projectId);
        notification.setIssueId(issueId);
        notification.setCategory(category);

        String subject = issueNumber == null || issueNumber.isBlank()
                ? safe(issueTitle)
                : issueNumber + " " + safe(issueTitle);
        String body = "「" + subject.trim() + "」的交接已被"
                + (operatorName == null || operatorName.isBlank() ? "他人" : operatorName)
                + "撤销，负责人已退回你。";
        notification.setBody(truncate(body, MAX_BODY));
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    public PageResult<NotificationVO> page(UUID userId, long pageNum, long pageSize) {
        long page = Math.max(1, pageNum);
        long size = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
        Long count = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId));
        long total = count == null ? 0 : count;
        if (total == 0) {
            return new PageResult<>(0, page, size, List.of());
        }
        List<Notification> records = notificationMapper.selectList(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + ((page - 1) * size)));
        return new PageResult<>(total, page, size, records.stream().map(NotificationService::toVO).toList());
    }

    public long unreadCount(UUID userId) {
        Long count = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
        return count == null ? 0 : count;
    }

    /**
     * 标记单条已读。先按 (id, user_id) 定位再更新，防止越权改他人通知。
     */
    @Transactional
    public void markRead(UUID userId, UUID id) {
        Notification notification = notificationMapper.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .last("LIMIT 1"));
        if (notification == null) {
            throw new ServiceException(404, "通知不存在");
        }
        if (notification.getReadAt() != null) {
            return;
        }
        notification.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        Integer rows = notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
        return rows == null ? 0 : rows;
    }

    private static NotificationVO toVO(Notification n) {
        return new NotificationVO(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getProjectId(), n.getIssueId(), n.getCategory(), n.getReadAt(), n.getCreatedAt());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
