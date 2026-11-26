package com.seven7m.kanban.service.sprint.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.enums.SprintStatus;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.sprint.CreateSprintRequest;
import com.seven7m.kanban.dto.sprint.SprintVO;
import com.seven7m.kanban.dto.sprint.UpdateSprintRequest;
import com.seven7m.kanban.entity.sprint.Sprint;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.sprint.SprintMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.sprint.SprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 迭代(Sprint)服务实现：单表 CRUD 走 {@link ServiceImpl} 的通用能力，
 * 用户表相关查询（创建人展示名）走注入的 {@code SysUserMapper}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintServiceImpl extends ServiceImpl<SprintMapper, Sprint> implements SprintService {

    private final SysUserMapper userMapper;

    @Override
    public List<SprintVO> listByProject(UUID projectId) {
        List<Sprint> sprints = list(Wrappers.<Sprint>lambdaQuery()
                .eq(Sprint::getProjectId, projectId)
                .orderByDesc(Sprint::getCreatedAt));
        // 创建人一次性批量查出，避免逐行查用户表（N+1）
        Set<UUID> creatorIds = sprints.stream()
                .map(Sprint::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = creatorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(creatorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return sprints.stream().map(s -> toVO(s, userMap)).collect(Collectors.toList());
    }

    @Override
    public SprintVO detail(UUID projectId, UUID id) {
        return toVO(requireSprint(projectId, id));
    }

    /** 迭代起止日期校验：结束日期不能早于开始日期 */
    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ServiceException(ErrorCode.SPRINT_DATE_RANGE_INVALID);
        }
    }

    @Override
    @Transactional
    public SprintVO create(UUID projectId, CreateSprintRequest req) {
        validateDateRange(req.getStartDate(), req.getEndDate());
        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());
        sprint.setProjectId(projectId);
        sprint.setName(req.getName());
        sprint.setGoal(req.getGoal());
        sprint.setStartDate(req.getStartDate());
        sprint.setEndDate(req.getEndDate());
        sprint.setStatus(SprintStatus.PLANNED.name());
        sprint.setCreatedBy(UserContext.getUserId());
        sprint.setUpdatedBy(UserContext.getUserId());
        save(sprint);
        return toVO(sprint);
    }

    @Override
    @Transactional
    public SprintVO update(UUID projectId, UUID id, UpdateSprintRequest req) {
        Sprint sprint = requireSprint(projectId, id);
        if (req.getName() != null) sprint.setName(req.getName());
        if (req.getGoal() != null) sprint.setGoal(req.getGoal());
        LocalDate newStart = req.getStartDate() != null ? req.getStartDate() : sprint.getStartDate();
        LocalDate newEnd = req.getEndDate() != null ? req.getEndDate() : sprint.getEndDate();
        validateDateRange(newStart, newEnd);
        if (req.getStartDate() != null) sprint.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) sprint.setEndDate(req.getEndDate());
        sprint.setUpdatedBy(UserContext.getUserId());
        updateById(sprint);
        return toVO(sprint);
    }

    @Override
    @Transactional
    public void remove(UUID projectId, UUID id) {
        requireSprint(projectId, id);
        removeById(id);
    }

    /** 开始迭代：PLANNED -> ACTIVE */
    @Override
    @Transactional
    public SprintVO start(UUID projectId, UUID id) {
        Sprint sprint = requireSprint(projectId, id);
        if (!SprintStatus.PLANNED.matches(sprint.getStatus())) {
            throw new ServiceException(ErrorCode.SPRINT_ALREADY_STARTED);
        }
        sprint.setStatus(SprintStatus.ACTIVE.name());
        sprint.setUpdatedBy(UserContext.getUserId());
        updateById(sprint);
        return toVO(sprint);
    }

    /** 完成迭代：ACTIVE -> COMPLETED。未完成 issue 保持现状（沿用现有约定，不强制迁移） */
    @Override
    @Transactional
    public SprintVO complete(UUID projectId, UUID id) {
        Sprint sprint = requireSprint(projectId, id);
        if (!SprintStatus.ACTIVE.matches(sprint.getStatus())) {
            throw new ServiceException(ErrorCode.SPRINT_NOT_ACTIVE);
        }
        sprint.setStatus(SprintStatus.COMPLETED.name());
        sprint.setUpdatedBy(UserContext.getUserId());
        updateById(sprint);
        return toVO(sprint);
    }

    /** 按 id 取迭代并校验归属项目：两个条件任一不满足都按"不存在"处理，不泄露别项目的数据 */
    private Sprint requireSprint(UUID projectId, UUID id) {
        Sprint sprint = getById(id);
        if (sprint == null || !sprint.getProjectId().equals(projectId)) {
            throw new ServiceException(ErrorCode.SPRINT_NOT_FOUND);
        }
        return sprint;
    }

    /** 单条场景（create/update/start/complete）：只查一次创建人 */
    private SprintVO toVO(Sprint s) {
        if (s.getCreatedBy() == null) {
            return toVO(s, Map.of());
        }
        SysUser u = userMapper.selectById(s.getCreatedBy());
        return toVO(s, u == null ? Map.of() : Map.of(u.getId(), u));
    }

    /** 列表场景：创建人由调用方批量查好传入 */
    private SprintVO toVO(Sprint s, Map<UUID, SysUser> userMap) {
        SprintVO vo = new SprintVO();
        vo.setId(s.getId());
        vo.setProjectId(s.getProjectId());
        vo.setName(s.getName());
        vo.setGoal(s.getGoal());
        vo.setStatus(s.getStatus());
        vo.setStartDate(s.getStartDate());
        vo.setEndDate(s.getEndDate());
        vo.setCreatedAt(s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
        vo.setUpdatedAt(s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
        if (s.getCreatedBy() != null) {
            SysUser u = userMap.get(s.getCreatedBy());
            if (u != null) {
                vo.setCreatorName(u.getDisplayName());
            }
        }
        return vo;
    }
}
