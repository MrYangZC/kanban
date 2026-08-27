package com.seven7m.kanban.service.announcement;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.announcement.AnnouncementVO;
import com.seven7m.kanban.dto.announcement.CreateAnnouncementRequest;
import com.seven7m.kanban.dto.announcement.UpdateAnnouncementRequest;
import com.seven7m.kanban.entity.announcement.Announcement;
import com.seven7m.kanban.mapper.announcement.AnnouncementMapper;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
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

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final SysUserMapper userMapper;

    public List<AnnouncementVO> list(UUID projectId) {
        List<Announcement> announcements = announcementMapper.selectList(Wrappers.<Announcement>lambdaQuery()
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

    @Transactional
    public AnnouncementVO create(UUID projectId, CreateAnnouncementRequest req) {
        Announcement a = new Announcement();
        a.setId(UUID.randomUUID());
        a.setProjectId(projectId);
        a.setTitle(req.getTitle());
        a.setContent(req.getContent());
        a.setPinned(req.getPinned() != null ? req.getPinned() : false);
        announcementMapper.insert(a);
        return toVO(a);
    }

    @Transactional
    public AnnouncementVO update(UUID projectId, UUID id, UpdateAnnouncementRequest req) {
        Announcement a = announcementMapper.selectById(id);
        if (a == null || !a.getProjectId().equals(projectId)) {
            throw new ServiceException(404, "公告不存在");
        }
        if (req.getTitle() != null) a.setTitle(req.getTitle());
        if (req.getContent() != null) a.setContent(req.getContent());
        if (req.getPinned() != null) a.setPinned(req.getPinned());
        announcementMapper.updateById(a);
        return toVO(a);
    }

    @Transactional
    public void delete(UUID projectId, UUID id) {
        Announcement a = announcementMapper.selectById(id);
        if (a == null || !a.getProjectId().equals(projectId)) {
            throw new ServiceException(404, "公告不存在");
        }
        announcementMapper.deleteById(id);
    }

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
            if (u != null) vo.setAuthorName(u.getDisplayName());
        }
        return vo;
    }

    private AnnouncementVO toVO(Announcement a) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(a.getId());
        vo.setProjectId(a.getProjectId());
        vo.setTitle(a.getTitle());
        vo.setContent(a.getContent());
        vo.setPinned(a.getPinned());
        vo.setCreatedAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        vo.setUpdatedAt(a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        if (a.getCreatedBy() != null) {
            SysUser u = userMapper.selectById(a.getCreatedBy());
            if (u != null) vo.setAuthorName(u.getDisplayName());
        }
        return vo;
    }
}