package com.seven7m.kanban.service.milestone.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.milestone.CreateMilestoneRequest;
import com.seven7m.kanban.dto.milestone.MilestoneVO;
import com.seven7m.kanban.dto.milestone.UpdateMilestoneRequest;
import com.seven7m.kanban.entity.milestone.Milestone;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.milestone.MilestoneMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.milestone.MilestoneService;
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
 * 里程碑(Milestone)服务实现：里程碑自身是单实体单 Mapper，CRUD 走 {@link ServiceImpl}。
 */
@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl extends ServiceImpl<MilestoneMapper, Milestone> implements MilestoneService {

    /** 新建里程碑的初始状态（库表默认值同为 OPEN，此处显式写入便于阅读） */
    private static final String DEFAULT_STATUS = "OPEN";

    /** 新建里程碑的初始完成度（百分比 0-100） */
    private static final int DEFAULT_PROGRESS = 0;

    /**
     * 里程碑名称上限。
     *
     * <p>与库表 {@code milestone.name varchar(200)} 对齐：服务层不提前拦，超长名称会以
     * PG 的「value too long」500 暴露，前端拿不到"是哪个字段超了多少"。
     */
    private static final int MILESTONE_NAME_MAX = 200;

    private final SysUserMapper userMapper;

    @Override
    public List<MilestoneVO> list(UUID projectId) {
        List<Milestone> milestones = list(Wrappers.<Milestone>lambdaQuery()
                .eq(Milestone::getProjectId, projectId)
                .orderByAsc(Milestone::getDueDate));
        Set<UUID> creatorIds = milestones.stream()
                .map(Milestone::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = creatorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(creatorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return milestones.stream().map(m -> toVO(m, userMap)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MilestoneVO create(UUID projectId, CreateMilestoneRequest req) {
        validateName(req.getName());
        Milestone m = new Milestone();
        m.setId(UUID.randomUUID());
        m.setProjectId(projectId);
        m.setName(req.getName());
        m.setDescription(req.getDescription());
        m.setDueDate(req.getDueDate());
        m.setStatus(DEFAULT_STATUS);
        m.setProgress(DEFAULT_PROGRESS);
        save(m);
        return toVO(m);
    }

    @Override
    @Transactional
    public MilestoneVO update(UUID projectId, UUID id, UpdateMilestoneRequest req) {
        Milestone m = requireMilestone(projectId, id);
        // 非空才更新：长度也只校验本次实际要写的值
        if (req.getName() != null) {
            validateName(req.getName());
            m.setName(req.getName());
        }
        if (req.getDescription() != null) m.setDescription(req.getDescription());
        if (req.getDueDate() != null) m.setDueDate(req.getDueDate());
        if (req.getStatus() != null) m.setStatus(req.getStatus());
        if (req.getProgress() != null) m.setProgress(req.getProgress());
        updateById(m);
        return toVO(m);
    }

    @Override
    @Transactional
    public void delete(UUID projectId, UUID id) {
        requireMilestone(projectId, id);
        removeById(id);
    }

    /** 里程碑名称长度校验：直接量本次要写入的原始值（与写入口径一致，不做 trim） */
    private void validateName(String name) {
        if (name != null && name.length() > MILESTONE_NAME_MAX) {
            throw new ServiceException(ErrorCode.FIELD_TOO_LONG, "里程碑名称", MILESTONE_NAME_MAX);
        }
    }

    /** 按 id 取里程碑并校验归属项目；不存在与不属于该项目统一按 404 处理 */
    private Milestone requireMilestone(UUID projectId, UUID id) {
        Milestone m = getById(id);
        if (m == null || !m.getProjectId().equals(projectId)) {
            throw new ServiceException(ErrorCode.MILESTONE_NOT_FOUND);
        }
        return m;
    }

    /** 单条场景：只查一次创建人，复用批量版本装配，避免两个 toVO 各写一遍字段映射 */
    private MilestoneVO toVO(Milestone m) {
        if (m.getCreatedBy() == null) {
            return toVO(m, Map.of());
        }
        SysUser u = userMapper.selectById(m.getCreatedBy());
        return toVO(m, u == null ? Map.of() : Map.of(u.getId(), u));
    }

    /** 列表场景：创建人由调用方批量查好传入 */
    private MilestoneVO toVO(Milestone m, Map<UUID, SysUser> userMap) {
        MilestoneVO vo = new MilestoneVO();
        vo.setId(m.getId());
        vo.setProjectId(m.getProjectId());
        vo.setName(m.getName());
        vo.setDescription(m.getDescription());
        vo.setDueDate(m.getDueDate());
        vo.setStatus(m.getStatus());
        vo.setProgress(m.getProgress());
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().toString() : null);
        if (m.getCreatedBy() != null) {
            SysUser u = userMap.get(m.getCreatedBy());
            if (u != null) {
                vo.setCreatorName(u.getDisplayName());
            }
        }
        return vo;
    }
}
