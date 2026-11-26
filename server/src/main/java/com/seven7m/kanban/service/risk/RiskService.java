package com.seven7m.kanban.service.risk;

import com.seven7m.kanban.dto.risk.CreateRiskRequest;
import com.seven7m.kanban.dto.risk.RiskVO;
import com.seven7m.kanban.dto.risk.UpdateRiskRequest;
import com.seven7m.kanban.entity.risk.Risk;

import java.util.List;
import java.util.UUID;

/**
 * 风险(Risk)服务：项目级风险 CRUD。
 *
 * <p>实现类继承 {@code ServiceImpl} 复用 MyBatis-Plus 的单表通用能力（按 id 取、保存、按条件取列表）；
 * 负责人/创建人的展示名要跨表查用户，仍走注入的 {@code SysUserMapper}，列表场景批量查一次。
 *
 * <p>实现见 {@link com.seven7m.kanban.service.risk.impl.RiskServiceImpl}。
 *
 * <p>接口<b>不</b>外露 {@code IService} 的通用写方法（{@code save/updateById/removeById}
 * 会绕过本域的权限与副作用校验），外部只能调用下面的领域方法。
 */
public interface RiskService {

    /** 项目下的风险列表（创建时间倒序；负责人/创建人展示名批量查一次） */
    List<RiskVO> listByProject(UUID projectId);

    /** 风险详情；不存在或不属于该项目都抛 404 */
    RiskVO detail(UUID projectId, UUID id);

    /** 新建风险：level 缺省 MEDIUM、status 缺省 OPEN */
    RiskVO create(UUID projectId, CreateRiskRequest req);

    /** 更新风险：非空即更新 + clearFields 显式清空负责人/截止日期 */
    RiskVO update(UUID projectId, UUID id, UpdateRiskRequest req);

    /** 删除风险 */
    void remove(UUID projectId, UUID id);
}
