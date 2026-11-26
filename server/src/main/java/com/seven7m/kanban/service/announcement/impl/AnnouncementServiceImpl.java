package com.seven7m.kanban.service.announcement.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.announcement.AnnouncementVO;
import com.seven7m.kanban.dto.announcement.CreateAnnouncementRequest;
import com.seven7m.kanban.dto.announcement.UpdateAnnouncementRequest;
import com.seven7m.kanban.entity.announcement.Announcement;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.announcement.AnnouncementMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.announcement.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公告(Announcement)服务实现：公告自身是单实体单 Mapper，CRUD 走 {@link ServiceImpl}。
 */
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement>
        implements AnnouncementService {

    /**
     * 公告标题上限。
     *
     * <p>与库表 {@code announcement.title varchar(200)} 对齐：服务层不提前拦，超长标题会以
     * PG 的「value too long」500 暴露，前端拿不到"是哪个字段超了多少"。
     */
    private static final int ANNOUNCEMENT_TITLE_MAX = 200;

    private final SysUserMapper userMapper;

    @Override
    public List<AnnouncementVO> list(UUID projectId) {
        List<Announcement> announcements = list(Wrappers.<Announcement>lambdaQuery()
                .eq(Announcement::getProjectId, projectId)
                .orderByDesc(Announcement::getPinned)
                .orderByDesc(Announcement::getCreatedAt));
        Set<UUID> creatorIds = announcements.stream()
                .map(Announcement::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = creatorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(creatorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return announcements.stream().map(a -> toVO(a, userMap)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AnnouncementVO create(UUID projectId, CreateAnnouncementRequest req) {
        validateTitle(req.getTitle());
        Announcement a = new Announcement();
        a.setId(UUID.randomUUID());
        a.setProjectId(projectId);
        a.setTitle(req.getTitle());
        a.setContent(req.getContent());
        a.setPinned(Boolean.TRUE.equals(req.getPinned()));
        save(a);
        return toVO(a);
    }

    @Override
    @Transactional
    public AnnouncementVO update(UUID projectId, UUID id, UpdateAnnouncementRequest req) {
        Announcement a = requireAnnouncement(projectId, id);
        // 非空才更新：长度也只校验本次实际要写的值
        if (req.getTitle() != null) {
            validateTitle(req.getTitle());
            a.setTitle(req.getTitle());
        }
        if (req.getContent() != null) a.setContent(req.getContent());
        if (req.getPinned() != null) a.setPinned(req.getPinned());
        updateById(a);
        return toVO(a);
    }

    @Override
    @Transactional
    public void delete(UUID projectId, UUID id) {
        requireAnnouncement(projectId, id);
        removeById(id);
    }

    /** 公告标题长度校验：直接量本次要写入的原始值（与写入口径一致，不做 trim） */
    private void validateTitle(String title) {
        if (title != null && title.length() > ANNOUNCEMENT_TITLE_MAX) {
            throw new ServiceException(ErrorCode.FIELD_TOO_LONG, "公告标题", ANNOUNCEMENT_TITLE_MAX);
        }
    }

    /** 按 id 取公告并校验归属项目；不存在与不属于该项目统一按 404 处理 */
    private Announcement requireAnnouncement(UUID projectId, UUID id) {
        Announcement a = getById(id);
        if (a == null || !a.getProjectId().equals(projectId)) {
            throw new ServiceException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return a;
    }

    /** 单条场景：只查一次作者，复用批量版本装配，避免两个 toVO 各写一遍字段映射 */
    private AnnouncementVO toVO(Announcement a) {
        if (a.getCreatedBy() == null) {
            return toVO(a, Map.of());
        }
        SysUser u = userMapper.selectById(a.getCreatedBy());
        return toVO(a, u == null ? Map.of() : Map.of(u.getId(), u));
    }

    /** 列表场景：作者由调用方批量查好传入 */
    private AnnouncementVO toVO(Announcement a, Map<UUID, SysUser> userMap) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(a.getId());
        vo.setProjectId(a.getProjectId());
        vo.setTitle(a.getTitle());
        vo.setContent(a.getContent());
        vo.setPinned(a.getPinned());
        vo.setCreatedAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        vo.setUpdatedAt(a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        if (a.getCreatedBy() != null) {
            SysUser u = userMap.get(a.getCreatedBy());
            if (u != null) {
                vo.setAuthorName(u.getDisplayName());
            }
        }
        return vo;
    }
}
