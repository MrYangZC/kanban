package com.seven7m.kanban.service.notification;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.dto.notification.NotificationVO;
import com.seven7m.kanban.entity.notification.Notification;

import java.util.UUID;

/**
 * 站内通知。当前只有交接（HANDOFF）一种来源。
 *
 * <p>继承 {@link IService} 复用 MyBatis-Plus 的单表通用能力（保存、计数、分页、按条件取列表）；
 * 实现里不再出现 {@code notificationMapper.xxx} 的样板，也不再拼 {@code last("LIMIT 1")} 这类 SQL 片段。
 *
 * <p>实现见 {@link com.seven7m.kanban.service.notification.impl.NotificationServiceImpl}。
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 交接通知，接收人即新的负责人。
     * 调用方处在状态流转事务内，实现里刻意不加 @Transactional（跟随外层事务）。
     *
     * @param landing 交接落点的一句话说明（「已放进迭代「S1」」等）；没配落点时为 null，
     *                正文与没有落点功能时完全一致
     */
    void notifyHandoff(UUID toUserId, UUID projectId, UUID issueId, String category,
                       String issueNumber, String issueTitle, String statusName, String note,
                       String landing);

    /**
     * 交接被撤销：工作项退回原负责人名下。
     *
     * <p>沿用 {@code HANDOFF} 类型而不是新开类型：铃铛跳转、未读计数、已读链路完全复用，
     * 差异只在标题与正文，前端不需要为它加分支。调用方同样处在撤销事务内，实现里不加 @Transactional。
     */
    void notifyHandoffRevoked(UUID toUserId, UUID projectId, UUID issueId, String category,
                              String issueNumber, String issueTitle, String operatorName);

    /** 当前用户的通知分页（创建时间倒序；page/pageSize 会被 clamp） */
    PageResult<NotificationVO> page(UUID userId, long pageNum, long pageSize);

    /** 当前用户的未读条数（铃铛角标） */
    long unreadCount(UUID userId);

    /** 标记单条已读；不属于该用户或不存在都抛 404 */
    void markRead(UUID userId, UUID id);

    /** 当前用户全部未读置已读，返回影响行数（前端用它刷新角标） */
    int markAllRead(UUID userId);
}
