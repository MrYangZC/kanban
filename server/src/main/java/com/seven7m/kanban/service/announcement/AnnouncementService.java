package com.seven7m.kanban.service.announcement;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.dto.announcement.AnnouncementVO;
import com.seven7m.kanban.dto.announcement.CreateAnnouncementRequest;
import com.seven7m.kanban.dto.announcement.UpdateAnnouncementRequest;
import com.seven7m.kanban.entity.announcement.Announcement;

import java.util.List;
import java.util.UUID;

/**
 * 公告(Announcement)服务：项目级公告的 CRUD。
 *
 * <p>继承 {@link IService} 复用 MyBatis-Plus 的单表通用能力（按 id 取、保存、删除、按条件取列表）；
 * 作者展示名要跨表查用户，仍走注入的 {@code SysUserMapper}，列表场景批量查一次避免 N+1。
 *
 * <p>实现见 {@link com.seven7m.kanban.service.announcement.impl.AnnouncementServiceImpl}。
 */
public interface AnnouncementService extends IService<Announcement> {

    /** 项目下的公告列表（置顶优先，其次按创建时间倒序） */
    List<AnnouncementVO> list(UUID projectId);

    /** 新建公告：pinned 缺省 false */
    AnnouncementVO create(UUID projectId, CreateAnnouncementRequest req);

    /** 更新公告：请求里的 null 表示"不修改" */
    AnnouncementVO update(UUID projectId, UUID id, UpdateAnnouncementRequest req);

    /** 删除公告 */
    void delete(UUID projectId, UUID id);
}
