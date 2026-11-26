package com.seven7m.kanban.service.risk.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.constant.ClearFields;
import com.seven7m.kanban.common.domain.NameIndex;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.util.UpdateWrappers;
import com.seven7m.kanban.dto.risk.CreateRiskRequest;
import com.seven7m.kanban.dto.risk.RiskVO;
import com.seven7m.kanban.dto.risk.UpdateRiskRequest;
import com.seven7m.kanban.entity.risk.Risk;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.risk.RiskMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.risk.RiskService;
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
 * 风险(Risk)服务实现：风险自身是单实体单 Mapper，CRUD 走 {@link ServiceImpl}；
 * 展示名索引、置空列的 UPDATE 构造器集中在本类，避免散落的 SQL 片段与裸 Map。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskServiceImpl extends ServiceImpl<RiskMapper, Risk> implements RiskService {

    /** 新建风险的缺省级别（risk.level 取值：LOW / MEDIUM / HIGH / CRITICAL） */
    private static final String DEFAULT_LEVEL = "MEDIUM";

    /** 新建风险的缺省状态（risk.status 取值：OPEN / MITIGATING / CLOSED） */
    private static final String DEFAULT_STATUS = "OPEN";

    /** UpdateRequest.clearFields 里的负责人字段名（与接口契约一致，不是列名） */
    private static final String FIELD_OWNER_ID = "ownerId";

    /** risk.id 列名：UPDATE 的 WHERE 只能用列名，固化成常量避免拼错 */
    private static final String COL_ID = "id";

    /** risk.owner_id 列名（置空时需要列名而非 lambda） */
    private static final String COL_OWNER_ID = "owner_id";

    /** risk.due_date 列名（置空时需要列名而非 lambda） */
    private static final String COL_DUE_DATE = "due_date";

    private final SysUserMapper userMapper;

    @Override
    public List<RiskVO> listByProject(UUID projectId) {
        List<Risk> list = list(Wrappers.<Risk>lambdaQuery()
                .eq(Risk::getProjectId, projectId)
                .orderByDesc(Risk::getCreatedAt));
        NameIndex<UUID> nameIndex = loadUserNames(list);
        return list.stream().map(r -> toVO(r, nameIndex)).collect(Collectors.toList());
    }

    @Override
    public RiskVO detail(UUID projectId, UUID id) {
        Risk risk = requireRisk(projectId, id);
        return toVO(risk, loadUserNames(List.of(risk)));
    }

    @Override
    @Transactional
    public RiskVO create(UUID projectId, CreateRiskRequest req) {
        Risk risk = new Risk();
        risk.setId(UUID.randomUUID());
        risk.setProjectId(projectId);
        risk.setTitle(req.getTitle());
        risk.setDescription(req.getDescription());
        risk.setLevel(req.getLevel() == null ? DEFAULT_LEVEL : req.getLevel());
        risk.setStatus(req.getStatus() == null ? DEFAULT_STATUS : req.getStatus());
        risk.setOwnerId(req.getOwnerId());
        risk.setDueDate(req.getDueDate());
        risk.setCreatedBy(UserContext.getUserId());
        risk.setUpdatedBy(UserContext.getUserId());
        save(risk);
        return toVO(risk, loadUserNames(List.of(risk)));
    }

    @Override
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
        // 与工作项更新（AbstractWorkItemService）保持同一套 clearFields 语义。
        // 置空写字面量 NULL 交给 UpdateWrappers.clearToNull：PG 无法从 #{null} 推断参数类型
        UpdateWrapper<Risk> uw = Wrappers.<Risk>update().eq(COL_ID, id);
        if (req.getClearFields() != null) {
            for (String field : req.getClearFields()) {
                switch (field) {
                    case FIELD_OWNER_ID -> {
                        risk.setOwnerId(null);
                        UpdateWrappers.clearToNull(uw, COL_OWNER_ID);
                    }
                    case ClearFields.DUE_DATE -> {
                        risk.setDueDate(null);
                        UpdateWrappers.clearToNull(uw, COL_DUE_DATE);
                    }
                    default -> { }
                }
            }
        }
        risk.setUpdatedBy(UserContext.getUserId());
        String sqlSet = uw.getSqlSet();
        if (sqlSet != null && !sqlSet.isEmpty()) {
            update(risk, uw);
        } else {
            updateById(risk);
        }
        return toVO(risk, loadUserNames(List.of(risk)));
    }

    @Override
    @Transactional
    public void remove(UUID projectId, UUID id) {
        requireRisk(projectId, id);
        removeById(id);
    }

    /** 按 id 取风险并校验归属项目；不存在与不属于该项目统一按 404 处理 */
    private Risk requireRisk(UUID projectId, UUID id) {
        Risk risk = getById(id);
        if (risk == null || !risk.getProjectId().equals(projectId)) {
            throw new ServiceException(ErrorCode.RISK_NOT_FOUND);
        }
        return risk;
    }

    /**
     * 批量加载负责人与创建人的展示名，避免逐条查库。
     *
     * <p>返回 {@link NameIndex} 而不是 {@code Map}：调用方只需要"按 id 取名字"这一个语义，
     * 键值含义与缺失处理都收在索引类型里，不再暴露裸容器。
     */
    private NameIndex<UUID> loadUserNames(List<Risk> risks) {
        Set<UUID> ids = risks.stream()
                .flatMap(r -> Stream.of(r.getOwnerId(), r.getCreatedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return NameIndex.empty();
        }
        Map<UUID, String> names = userMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getId, ids))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(), (a, b) -> a));
        return NameIndex.of(names);
    }

    private RiskVO toVO(Risk r, NameIndex<UUID> names) {
        RiskVO vo = new RiskVO();
        vo.setId(r.getId());
        vo.setProjectId(r.getProjectId());
        vo.setTitle(r.getTitle());
        vo.setDescription(r.getDescription());
        vo.setLevel(r.getLevel());
        vo.setStatus(r.getStatus());
        vo.setOwnerId(r.getOwnerId());
        vo.setOwnerName(names.nameOf(r.getOwnerId()));
        vo.setDueDate(r.getDueDate());
        vo.setCreatorName(names.nameOf(r.getCreatedBy()));
        vo.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        vo.setUpdatedAt(r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
        return vo;
    }
}
