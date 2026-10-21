package com.seven7m.kanban.service.risk;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.risk.CreateRiskRequest;
import com.seven7m.kanban.dto.risk.RiskVO;
import com.seven7m.kanban.dto.risk.UpdateRiskRequest;
import com.seven7m.kanban.entity.risk.Risk;
import com.seven7m.kanban.mapper.risk.RiskMapper;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 风险(Risk)服务：项目级风险 CRUD。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskMapper riskMapper;
    private final SysUserMapper userMapper;

    public List<RiskVO> listByProject(UUID projectId) {
        List<Risk> list = riskMapper.selectList(
                Wrappers.<Risk>lambdaQuery().eq(Risk::getProjectId, projectId).orderByDesc(Risk::getCreatedAt));
        Map<UUID, String> nameMap = loadUserNames(list);
        return list.stream().map(r -> toVO(r, nameMap)).collect(Collectors.toList());
    }

    public RiskVO detail(UUID projectId, UUID id) {
        Risk risk = requireRisk(projectId, id);
        return toVO(risk, loadUserNames(List.of(risk)));
    }

    @Transactional
    public RiskVO create(UUID projectId, CreateRiskRequest req) {
        Risk risk = new Risk();
        risk.setId(UUID.randomUUID());
        risk.setProjectId(projectId);
        risk.setTitle(req.getTitle());
        risk.setDescription(req.getDescription());
        risk.setLevel(req.getLevel() == null ? "MEDIUM" : req.getLevel());
        risk.setStatus(req.getStatus() == null ? "OPEN" : req.getStatus());
        risk.setOwnerId(req.getOwnerId());
        risk.setDueDate(req.getDueDate());
        risk.setCreatedBy(UserContext.getUserId());
        risk.setUpdatedBy(UserContext.getUserId());
        riskMapper.insert(risk);
        return toVO(risk, loadUserNames(List.of(risk)));
    }

    @Transactional
    public RiskVO update(UUID projectId, UUID id, UpdateRiskRequest req) {
        Risk risk = requireRisk(projectId, id);
        if (req.getTitle() != null) risk.setTitle(req.getTitle());
        if (req.getDescription() != null) risk.setDescription(req.getDescription());
        if (req.getLevel() != null) risk.setLevel(req.getLevel());
        if (req.getStatus() != null) risk.setStatus(req.getStatus());
        if (req.getOwnerId() != null) risk.setOwnerId(req.getOwnerId());
        if (req.getDueDate() != null) risk.setDueDate(req.getDueDate());
        // updateById 会跳过 null 字段，所以要置空的列必须用 UpdateWrapper 显式 set，
        // 与工作项更新（BaseWorkItemService）保持同一套 clearFields 语义
        var uw = Wrappers.<Risk>lambdaUpdate().eq(Risk::getId, id);
        if (req.getClearFields() != null) {
            for (String field : req.getClearFields()) {
                switch (field) {
                    case "ownerId" -> {
                        risk.setOwnerId(null);
                        uw.set(Risk::getOwnerId, null);
                    }
                    case "dueDate" -> {
                        risk.setDueDate(null);
                        uw.set(Risk::getDueDate, null);
                    }
                    default -> { }
                }
            }
        }
        risk.setUpdatedBy(UserContext.getUserId());
        if (uw.getSqlSet() != null && !uw.getSqlSet().isEmpty()) {
            riskMapper.update(risk, uw);
        } else {
            riskMapper.updateById(risk);
        }
        return toVO(risk, loadUserNames(List.of(risk)));
    }

    @Transactional
    public void remove(UUID projectId, UUID id) {
        requireRisk(projectId, id);
        riskMapper.deleteById(id);
    }

    private Risk requireRisk(UUID projectId, UUID id) {
        Risk risk = riskMapper.selectById(id);
        if (risk == null || !risk.getProjectId().equals(projectId)) {
            throw new ServiceException(404, "风险不存在");
        }
        return risk;
    }

    /** 批量加载负责人与创建人的展示名，避免逐条查库 */
    private Map<UUID, String> loadUserNames(List<Risk> risks) {
        Set<UUID> ids = risks.stream()
                .flatMap(r -> Stream.of(r.getOwnerId(), r.getCreatedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getId, ids))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(), (a, b) -> a));
    }

    private RiskVO toVO(Risk r, Map<UUID, String> nameMap) {
        RiskVO vo = new RiskVO();
        vo.setId(r.getId());
        vo.setProjectId(r.getProjectId());
        vo.setTitle(r.getTitle());
        vo.setDescription(r.getDescription());
        vo.setLevel(r.getLevel());
        vo.setStatus(r.getStatus());
        vo.setOwnerId(r.getOwnerId());
        vo.setOwnerName(r.getOwnerId() == null ? null : nameMap.get(r.getOwnerId()));
        vo.setDueDate(r.getDueDate());
        vo.setCreatorName(r.getCreatedBy() == null ? null : nameMap.get(r.getCreatedBy()));
        vo.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        vo.setUpdatedAt(r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
        return vo;
    }
}
