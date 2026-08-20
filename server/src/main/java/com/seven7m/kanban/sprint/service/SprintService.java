package com.seven7m.kanban.sprint.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.sprint.dto.CreateSprintRequest;
import com.seven7m.kanban.sprint.dto.SprintVO;
import com.seven7m.kanban.sprint.dto.UpdateSprintRequest;
import com.seven7m.kanban.sprint.entity.Sprint;
import com.seven7m.kanban.sprint.mapper.SprintMapper;
import com.seven7m.kanban.user.entity.SysUser;
import com.seven7m.kanban.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 迭代(Sprint)服务：CRUD + start/complete 业务动作。
 * 状态机：PLANNED -> ACTIVE(start) -> COMPLETED(complete)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;
    private final SysUserMapper userMapper;

    public List<SprintVO> listByProject(UUID projectId) {
        List<Sprint> list = sprintMapper.selectList(
                Wrappers.<Sprint>lambdaQuery().eq(Sprint::getProjectId, projectId).orderByDesc(Sprint::getCreatedAt));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    public SprintVO detail(UUID projectId, UUID id) {
        return toVO(requireSprint(projectId, id));
    }

    @Transactional
    public SprintVO create(UUID projectId, CreateSprintRequest req) {
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(req.getName());
        sprint.setGoal(req.getGoal());
        sprint.setStartDate(req.getStartDate());
        sprint.setEndDate(req.getEndDate());
        sprint.setStatus("PLANNED");
        sprint.setCreatedBy(UserContext.getUserId());
        sprint.setUpdatedBy(UserContext.getUserId());
        sprintMapper.insert(sprint);
        return toVO(sprint);
    }

    @Transactional
    public SprintVO update(UUID projectId, UUID id, UpdateSprintRequest req) {
        Sprint sprint = requireSprint(projectId, id);
        if (req.getName() != null) sprint.setName(req.getName());
        if (req.getGoal() != null) sprint.setGoal(req.getGoal());
        if (req.getStartDate() != null) sprint.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) sprint.setEndDate(req.getEndDate());
        sprint.setUpdatedBy(UserContext.getUserId());
        sprintMapper.updateById(sprint);
        return toVO(sprint);
    }

    @Transactional
    public void remove(UUID projectId, UUID id) {
        requireSprint(projectId, id);
        sprintMapper.deleteById(id);
    }

    /** 开始迭代：PLANNED -> ACTIVE */
    @Transactional
    public SprintVO start(UUID projectId, UUID id) {
        Sprint sprint = requireSprint(projectId, id);
        if (!"PLANNED".equals(sprint.getStatus())) {
            throw new ServiceException(400, "仅 PLANNED 状态可开始迭代");
        }
        sprint.setStatus("ACTIVE");
        sprint.setUpdatedBy(UserContext.getUserId());
        sprintMapper.updateById(sprint);
        return toVO(sprint);
    }

    /** 完成迭代：ACTIVE -> COMPLETED。未完成 issue 保持现状（沿用现有约定，不强制迁移） */
    @Transactional
    public SprintVO complete(UUID projectId, UUID id) {
        Sprint sprint = requireSprint(projectId, id);
        if (!"ACTIVE".equals(sprint.getStatus())) {
            throw new ServiceException(400, "仅 ACTIVE 状态可完成迭代");
        }
        sprint.setStatus("COMPLETED");
        sprint.setUpdatedBy(UserContext.getUserId());
        sprintMapper.updateById(sprint);
        return toVO(sprint);
    }

    private Sprint requireSprint(UUID projectId, UUID id) {
        Sprint sprint = sprintMapper.selectById(id);
        if (sprint == null || !sprint.getProjectId().equals(projectId)) {
            throw new ServiceException(404, "迭代不存在");
        }
        return sprint;
    }

    private SprintVO toVO(Sprint s) {
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
            SysUser u = userMapper.selectById(s.getCreatedBy());
            if (u != null) vo.setCreatorName(u.getDisplayName());
        }
        return vo;
    }
}
