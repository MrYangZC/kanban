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

    @Transactional(readOnly = true)
    public DashboardVO getDashboard(UUID projectId) {
        DashboardVO vo = new DashboardVO();

        // 概览
        DashboardMapper.DashboardSummaryView s = dashboardMapper.summary(projectId);
        vo.getSummary().setTotal(s.total);
        vo.getSummary().setDone(s.done);
        vo.getSummary().setOverdue(s.overdue);
        vo.getSummary().setInProgress(Math.max(0, s.total - s.done));

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
        List<DashboardMapper.DateCountView> created = dashboardMapper.dailyCreated(projectId);
        List<DashboardMapper.DateCountView> completed = dashboardMapper.dailyCompleted(projectId);

        Map<String, Long> completedByDate = new LinkedHashMap<>();
        for (DashboardMapper.DateCountView c : completed) {
            completedByDate.merge(c.date, c.completed, Long::sum);
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(13);
        long cumCreated = 0;
        long cumDone = totalDone - sumRecent(completed, start); // 起点前已完成的基线
        List<DashboardVO.BurndownPoint> points = new ArrayList<>();
        for (int d = 0; d < 14; d++) {
            LocalDate day = start.plusDays(d);
            String key = day.toString();
            // 当日创建累计
            for (DashboardMapper.DateCountView c : created) {
                if (c.date.compareTo(key) <= 0) cumCreated = totalCreatedUpto(created, key);
            }
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

    private long totalCreatedUpto(List<DashboardMapper.DateCountView> created, String key) {
        long sum = 0;
        for (DashboardMapper.DateCountView c : created) {
            if (c.date.compareTo(key) <= 0) sum += c.created;
        }
        return sum;
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
