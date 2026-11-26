package com.seven7m.kanban.service.milestone;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.dto.milestone.CreateMilestoneRequest;
import com.seven7m.kanban.dto.milestone.MilestoneVO;
import com.seven7m.kanban.dto.milestone.UpdateMilestoneRequest;
import com.seven7m.kanban.entity.milestone.Milestone;

import java.util.List;
import java.util.UUID;

/**
 * 里程碑(Milestone)服务：项目级里程碑的 CRUD。
 *
 * <p>继承 {@link IService} 复用 MyBatis-Plus 的单表通用能力（按 id 取、保存、删除、按条件取列表）；
 * 创建人展示名需要跨表查用户，仍走注入的 {@code SysUserMapper}，且列表场景批量查一次避免 N+1。
 *
 * <p>实现见 {@link com.seven7m.kanban.service.milestone.impl.MilestoneServiceImpl}。
 */
public interface MilestoneService extends IService<Milestone> {

    /** 项目下的里程碑列表（按截止日期升序，创建人批量查出） */
    List<MilestoneVO> list(UUID projectId);

    /** 新建里程碑：初始状态 OPEN、完成度 0 */
    MilestoneVO create(UUID projectId, CreateMilestoneRequest req);

    /** 更新里程碑：请求里的 null 表示"不修改" */
    MilestoneVO update(UUID projectId, UUID id, UpdateMilestoneRequest req);

    /** 删除里程碑 */
    void delete(UUID projectId, UUID id);
}
