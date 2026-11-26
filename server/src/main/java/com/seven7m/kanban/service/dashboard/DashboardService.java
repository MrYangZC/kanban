package com.seven7m.kanban.service.dashboard;

import com.seven7m.kanban.dto.dashboard.DashboardVO;

import java.util.UUID;

/**
 * 统计报表聚合服务（只读）。对应设计文档「统计报表」：完成率、工作量、周期时间、Burndown。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.dashboard.impl.DashboardServiceImpl}。
 * 不继承 {@code IService}：这里是三张物理表 UNION ALL 的只读度量聚合，
 * 没有单一实体与单一 Mapper，且全部方法只读。
 */
public interface DashboardService {

    /**
     * 项目度量页的全部数据：概览、分类型概览、状态/优先级/负责人分布、周期时间、14 天燃尽图。
     * 口径统一在 {@code DashboardMapper} 的 SQL 里（排除归档、完成按 issue_status.category），
     * 本方法只做装配与兜底。
     */
    DashboardVO getDashboard(UUID projectId);
}
