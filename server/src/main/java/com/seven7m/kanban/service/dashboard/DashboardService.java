package com.seven7m.kanban.service.dashboard;

import com.seven7m.kanban.dto.dashboard.DashboardVO;
import com.seven7m.kanban.mapper.dashboard.CycleTimeRow;
import com.seven7m.kanban.mapper.dashboard.DailyCompletedRow;
import com.seven7m.kanban.mapper.dashboard.DailyCreatedRow;
import com.seven7m.kanban.mapper.dashboard.DashboardMapper;
import com.seven7m.kanban.mapper.dashboard.DashboardSummaryRow;
import com.seven7m.kanban.mapper.dashboard.TypeSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统计报表聚合服务（只读）。对应设计文档「统计报表」：完成率、工作量、周期时间、Burndown。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;

    /** 类型码 → 展示名，同时决定度量页卡片的顺序 */
    private static final Map<String, String> TYPE_NAMES = new LinkedHashMap<>();

    static {
        TYPE_NAMES.put("REQUIREMENT", "需求");
        TYPE_NAMES.put("BUG", "缺陷");
        TYPE_NAMES.put("TASK", "任务");
    }

    private DashboardVO.TypeSummary emptyTypeSummary(String typeCode) {
        DashboardVO.TypeSummary ts = new DashboardVO.TypeSummary();
        ts.setTypeCode(typeCode);
        ts.setTypeName(TYPE_NAMES.get(typeCode));
        return ts;
    }

    @Transactional(readOnly = true)
    public DashboardVO getDashboard(UUID projectId) {
        DashboardVO vo = new DashboardVO();

        // 概览
        DashboardSummaryRow s = dashboardMapper.summary(projectId);
        vo.getSummary().setTotal(orZero(s.getTotal()));
        vo.getSummary().setTodo(orZero(s.getTodo()));
        vo.getSummary().setDone(orZero(s.getDone()));
        vo.getSummary().setOverdue(orZero(s.getOverdue()));
        // 之前这里是 total - done（其实是「未完成」），会把待处理的活儿算进行中；
        // 现在按 issue_status.category 真实统计，前端卡片也据此拆分「待处理 / 处理中」
        vo.getSummary().setInProgress(orZero(s.getInProgress()));

        // 分类型概览：需求 / 缺陷 / 任务，顺序固定（前端按此顺序渲染并决定跳转目标）
        Map<String, DashboardVO.TypeSummary> byCode = new LinkedHashMap<>();
        for (TypeSummaryRow t : dashboardMapper.typeSummaries(projectId)) {
            DashboardVO.TypeSummary ts = new DashboardVO.TypeSummary();
            ts.setTypeCode(t.getTypeCode());
            ts.setTypeName(TYPE_NAMES.getOrDefault(t.getTypeCode(), t.getTypeCode()));
            ts.setTotal(orZero(t.getTotal()));
            ts.setTodo(orZero(t.getTodo()));
            ts.setInProgress(orZero(t.getInProgress()));
            ts.setDone(orZero(t.getDone()));
            ts.setOverdue(orZero(t.getOverdue()));
            byCode.put(t.getTypeCode(), ts);
        }
        // 某类型一条都没有时 SQL 不会返回该行，这里补 0，保证度量页三张卡片始终齐全
        for (String code : TYPE_NAMES.keySet()) {
            vo.getTypeSummaries().add(byCode.getOrDefault(code, emptyTypeSummary(code)));
        }

        // 分布
        vo.setStatusDist(dashboardMapper.statusDist(projectId));
        vo.setPriorityDist(dashboardMapper.priorityDist(projectId));
        vo.setAssigneeDist(dashboardMapper.assigneeDist(projectId));

        // 周期时间
        CycleTimeRow ct = dashboardMapper.cycleTime(projectId);
        vo.getCycleTime().setAvgHours(round2(orZero(ct.getAvgHours())));
        vo.getCycleTime().setMaxHours(round2(orZero(ct.getMaxHours())));

        // 燃尽图：最近 14 天。剩余 = 累计创建 - 累计完成（同口径、均排除归档）
        buildBurndown(vo, projectId);

        return vo;
    }

    /**
     * 14 天燃尽图。
     *
     * <p>此前用"当前总数"反推历史：14 天窗口内新建的工作项被当成"一开始就存在"，
     * 曲线左端整体偏低。现在按 created_at / done_at 逐日累计：
     * 剩余(t) = 窗口前已创建 + 窗口内累计新建 - 窗口前已完成 - 窗口内累计完成。
     */
    private void buildBurndown(DashboardVO vo, UUID projectId) {
        LocalDate end = LocalDate.now();
        LocalDate startDate = end.minusDays(13);
        LocalDateTime windowStart = startDate.atStartOfDay();

        long cumCreated = dashboardMapper.countCreatedBefore(projectId, windowStart);
        long cumDone = dashboardMapper.countDoneBefore(projectId, windowStart);

        Map<String, Long> createdByDate = createdByDate(dashboardMapper.dailyCreated(projectId, windowStart));
        Map<String, Long> completedByDate = completedByDate(dashboardMapper.dailyCompleted(projectId, windowStart));

        List<DashboardVO.BurndownPoint> points = new ArrayList<>();
        for (int d = 0; d < 14; d++) {
            String key = startDate.plusDays(d).toString();
            cumCreated += createdByDate.getOrDefault(key, 0L);
            cumDone += completedByDate.getOrDefault(key, 0L);
            DashboardVO.BurndownPoint p = new DashboardVO.BurndownPoint();
            p.setDate(key);
            p.setRemaining(Math.max(0, cumCreated - cumDone));
            p.setCompleted(cumDone);
            points.add(p);
        }
        vo.setBurndown(points);
    }

    private Map<String, Long> createdByDate(List<DailyCreatedRow> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (DailyCreatedRow row : rows) {
            map.merge(row.getDate(), orZero(row.getCreated()), Long::sum);
        }
        return map;
    }

    private Map<String, Long> completedByDate(List<DailyCompletedRow> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (DailyCompletedRow row : rows) {
            map.merge(row.getDate(), orZero(row.getCompleted()), Long::sum);
        }
        return map;
    }

    /** 打包方法：SQL 侧的 COUNT/AVG 理论上不会为 null，但显式兜底避免拆箱 NPE */
    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
