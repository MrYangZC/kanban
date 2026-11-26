package com.seven7m.kanban.service.sprint;

import com.seven7m.kanban.dto.sprint.CreateSprintRequest;
import com.seven7m.kanban.dto.sprint.SprintVO;
import com.seven7m.kanban.dto.sprint.UpdateSprintRequest;
import com.seven7m.kanban.entity.sprint.Sprint;

import java.util.List;
import java.util.UUID;

/**
 * 迭代(Sprint)服务：CRUD + start/complete 业务动作。
 * 状态机：PLANNED -> ACTIVE(start) -> COMPLETED(complete)。
 *
 * <p>实现类继承 {@code ServiceImpl} 复用 MyBatis-Plus 的单表通用能力（按 id 取、保存、按条件取列表），
 * 实现里不再写 {@code sprintMapper.selectById / insert / updateById / deleteById} 这些样板；
 * 唯一的跨表动作是"查创建人展示名"，仍走注入的 {@code SysUserMapper}。
 *
 * <p>实现见 {@link com.seven7m.kanban.service.sprint.impl.SprintServiceImpl}。
 *
 * <p>接口<b>不</b>外露 {@code IService} 的通用写方法（{@code save/updateById/removeById}
 * 会绕过本域的权限与副作用校验），外部只能调用下面的领域方法。
 */
public interface SprintService {

    /** 项目下的迭代列表（创建时间倒序；创建人一次批量查出，避免逐行查用户表的 N+1） */
    List<SprintVO> listByProject(UUID projectId);

    /** 迭代详情；不存在或不属于该项目都抛 404 */
    SprintVO detail(UUID projectId, UUID id);

    /** 新建迭代：初始状态 PLANNED，写入前校验起止日期 */
    SprintVO create(UUID projectId, CreateSprintRequest req);

    /** 更新迭代：请求里的 null 表示"不修改"，改完按最终值再校验一次起止日期 */
    SprintVO update(UUID projectId, UUID id, UpdateSprintRequest req);

    /** 删除迭代 */
    void remove(UUID projectId, UUID id);

    /** 开始迭代：PLANNED -> ACTIVE */
    SprintVO start(UUID projectId, UUID id);

    /** 完成迭代：ACTIVE -> COMPLETED */
    SprintVO complete(UUID projectId, UUID id);
}
