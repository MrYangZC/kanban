package com.seven7m.kanban.service.dashboard;

import com.seven7m.kanban.dto.dashboard.DashboardVO;
import com.seven7m.kanban.mapper.dashboard.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
        DashboardMapper.DashboardSummaryView s = dashboardMapper.summary(projectId);
        vo.getSummary().setTotal(s.total);
        vo.getSummary().setTodo(s.todo);
        vo.getSummary().setDone(s.done);
        vo.getSummary().setOverdue(s.overdue);
        // 之前这里是 total - done（其实是「未完成」），会把待处理的活儿算进行中；
        // 现在按 issue_status.category 真实统计，前端卡片也据此拆分「待处理 / 处理中」
        vo.getSummary().setInProgress(s.inProgress);

        // 分类型概览：需求 / 缺陷 / 任务，顺序固定（前端按此顺序渲染并决定跳转目标）
        Map<String, DashboardVO.TypeSummary> byCode = new LinkedHashMap<>();
        for (DashboardMapper.TypeSummaryView t : dashboardMapper.typeSummaries(projectId)) {
            DashboardVO.TypeSummary ts = new DashboardVO.TypeSummary();
            ts.setTypeCode(t.typeCode);
            ts.setTypeName(TYPE_NAMES.getOrDefault(t.typeCode, t.typeCode));
            ts.setTotal(t.total);
            ts.setTodo(t.todo);
            ts.setInProgress(t.inProgress);
            ts.setDone(t.done);
            ts.setOverdue(t.overdue);
            byCode.put(t.typeCode, ts);
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
        DashboardMapper.CycleTimeView ct = dashboardMapper.cycleTime(projectId);
        vo.getCycleTime().setAvgHours(round2(ct.avgHours));
        vo.getCycleTime().setMaxHours(round2(ct.maxHours));

        // 燃尽图：最近 14 天，remaining = 总创建累计 - 累计完成
        buildBurndown(vo, projectId, s.total, s.done);

        return vo;
    }

    private void buildBurndown(DashboardVO vo, UUID projectId, long totalCount, long totalDone) {
        List<DashboardMapper.DateCountView> completed = dashboardMapper.dailyCompleted(projectId);

        Map<String, Long> completedByDate = new LinkedHashMap<>();
        for (DashboardMapper.DateCountView c : completed) {
            completedByDate.merge(c.date, c.completed, Long::sum);
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(13);
        // 注：此前这里有一段计算 cumCreated 的嵌套循环，但其结果从未被读取（燃尽图
        // 只消费 cumDone），属死代码，且连带一次无用的 dailyCreated 查询，已移除。
        long cumDone = totalDone - sumRecent(completed, start); // 起点前已完成的基线
        List<DashboardVO.BurndownPoint> points = new ArrayList<>();
        for (int d = 0; d < 14; d++) {
            LocalDate day = start.plusDays(d);
            String key = day.toString();
            long doneToday = completedByDate.getOrDefault(key, 0L);
            cumDone += doneToday;
            long remaining = Math.max(0, totalCount - cumDone);
            DashboardVO.BurndownPoint p = new DashboardVO.BurndownPoint();
            p.setDate(key);
            p.setRemaining(remaining);
            p.setCompleted(cumDone);
            points.add(p);
        }
        vo.setBurndown(points);
    }

    private long sumRecent(List<DashboardMapper.DateCountView> completed, LocalDate start) {
        long sum = 0;
        for (DashboardMapper.DateCountView c : completed) {
            if (LocalDate.parse(c.date).isBefore(start)) sum += c.completed;
        }
        return sum;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
