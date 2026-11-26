package com.seven7m.kanban.service.notification.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.constant.PageLimits;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.notification.NotificationVO;
import com.seven7m.kanban.entity.notification.Notification;
import com.seven7m.kanban.mapper.notification.NotificationMapper;
import com.seven7m.kanban.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 站内通知实现。当前只有交接（HANDOFF）一种来源。
 *
 * <p>历史坑（保留以警示）：项目曾经没有注册 MyBatis-Plus 分页插件，当时只能手写 LIMIT/OFFSET；
 * 插件缺失时 {@code selectPage} 不报错，而是静默忽略分页、返回全表——接口"看着能用"。
 * 现在 {@code MybatisPlusConfig} 已显式注册分页插件，本类改用 {@code page}/{@code count}；
 * 若那条配置被删掉，这里就会退化成"一页返回全部"。
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    /** 通知类型：工作流交接（撤销也复用同一类型，靠标题/正文区分） */
    private static final String TYPE_HANDOFF = "HANDOFF";

    /** 通知列表每页上限：铃铛下拉一次最多看这么多条 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 与 notification.body 列宽一致 */
    private static final int MAX_BODY = 500;

    /** 收到交接的通知标题 */
    private static final String TITLE_HANDOFF = "收到一条交接工作项";

    /** 交接撤销的通知标题 */
    private static final String TITLE_HANDOFF_REVOKED = "工作项交接已撤销";

    @Override
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
        notification.setTitle(TITLE_HANDOFF);
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
        save(notification);
    }

    @Override
    public void notifyHandoffRevoked(UUID toUserId, UUID projectId, UUID issueId, String category,
                                     String issueNumber, String issueTitle, String operatorName) {
        if (toUserId == null || issueId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(toUserId);
        notification.setType(TYPE_HANDOFF);
        notification.setTitle(TITLE_HANDOFF_REVOKED);
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
        save(notification);
    }

    @Override
    public PageResult<NotificationVO> page(UUID userId, long pageNum, long pageSize) {
        long current = Math.max(PageLimits.MIN_PAGE, pageNum);
        long size = Math.min(MAX_PAGE_SIZE, Math.max(PageLimits.MIN_PAGE, pageSize));
        long total = count(Wrappers.<Notification>lambdaQuery().eq(Notification::getUserId, userId));
        if (total == 0) {
            return new PageResult<>(0, current, size, List.of());
        }
        // 分页交给 MyBatis-Plus：Page(searchCount=false) 沿用上面的 count，
        // LIMIT/OFFSET 由分页插件以绑定参数生成，不再拼 SQL 字符串
        Page<Notification> pageReq = new Page<>(current, size, false);
        List<Notification> records = page(pageReq, Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)).getRecords();
        return new PageResult<>(total, current, size,
                records.stream().map(NotificationServiceImpl::toVO).toList());
    }

    @Override
    public long unreadCount(UUID userId) {
        return count(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
    }

    /**
     * 标记单条已读。先按 (id, user_id) 定位再更新，防止越权改他人通知。
     *
     * <p>原实现用 selectOne 并在条件构造器末尾追加 LIMIT 1，等于把 SQL 片段塞进了 wrapper；
     * 这里改成 {@code list(...).stream().findFirst()}：条件完全等价，且不再拼 SQL。
     */
    @Override
    @Transactional
    public void markRead(UUID userId, UUID id) {
        Notification notification = list(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)).stream().findFirst().orElse(null);
        if (notification == null) {
            throw new ServiceException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if (notification.getReadAt() != null) {
            return;
        }
        notification.setReadAt(LocalDateTime.now());
        updateById(notification);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        // 接口回"影响行数"给前端刷新角标，父类 update(wrapper) 只回 boolean，
        // 所以这里走 baseMapper 的 update(entity, wrapper) 拿到真实行数
        Integer rows = baseMapper.update(null, Wrappers.<Notification>lambdaUpdate()
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
