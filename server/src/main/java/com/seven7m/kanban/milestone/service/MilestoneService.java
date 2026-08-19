package com.seven7m.kanban.milestone.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.milestone.dto.CreateMilestoneRequest;
import com.seven7m.kanban.milestone.dto.MilestoneVO;
import com.seven7m.kanban.milestone.dto.UpdateMilestoneRequest;
import com.seven7m.kanban.milestone.entity.Milestone;
import com.seven7m.kanban.milestone.mapper.MilestoneMapper;
import com.seven7m.kanban.user.entity.SysUser;
import com.seven7m.kanban.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneMapper milestoneMapper;
    private final SysUserMapper userMapper;

    public List<MilestoneVO> list(UUID projectId) {
        return milestoneMapper.selectList(Wrappers.<Milestone>lambdaQuery()
                        .eq(Milestone::getProjectId, projectId)
                        .orderByAsc(Milestone::getDueDate))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public MilestoneVO create(UUID projectId, CreateMilestoneRequest req) {
        Milestone m = new Milestone();
        m.setProjectId(projectId);
        m.setName(req.getName());
        m.setDescription(req.getDescription());
        m.setDueDate(req.getDueDate());
        m.setStatus("OPEN");
        m.setProgress(0);
        milestoneMapper.insert(m);
        return toVO(m);
    }

    @Transactional
    public MilestoneVO update(UUID projectId, UUID id, UpdateMilestoneRequest req) {
        Milestone m = milestoneMapper.selectById(id);
        if (m == null || !m.getProjectId().equals(projectId)) {
            throw new ServiceException(404, "里程碑不存在");
        }
        if (req.getName() != null) m.setName(req.getName());
        if (req.getDescription() != null) m.setDescription(req.getDescription());
        if (req.getDueDate() != null) m.setDueDate(req.getDueDate());
        if (req.getStatus() != null) m.setStatus(req.getStatus());
        if (req.getProgress() != null) m.setProgress(req.getProgress());
        milestoneMapper.updateById(m);
        return toVO(m);
    }

    @Transactional
    public void delete(UUID projectId, UUID id) {
        Milestone m = milestoneMapper.selectById(id);
        if (m == null || !m.getProjectId().equals(projectId)) {
            throw new ServiceException(404, "里程碑不存在");
        }
        milestoneMapper.deleteById(id);
    }

    private MilestoneVO toVO(Milestone m) {
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
            SysUser u = userMapper.selectById(m.getCreatedBy());
            if (u != null) vo.setCreatorName(u.getDisplayName());
        }
        return vo;
    }
}