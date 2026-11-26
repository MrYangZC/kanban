package com.seven7m.kanban.service.dashboard.impl;

import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.dashboard.DashboardVO;
import com.seven7m.kanban.mapper.dashboard.CycleTimeRow;
import com.seven7m.kanban.mapper.dashboard.DailyCompletedRow;
import com.seven7m.kanban.mapper.dashboard.DailyCreatedRow;
import com.seven7m.kanban.mapper.dashboard.DashboardMapper;
import com.seven7m.kanban.mapper.dashboard.DashboardSummaryRow;
import com.seven7m.kanban.mapper.dashboard.TypeSummaryRow;
import com.seven7m.kanban.service.dashboard.DashboardService;
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
public class DashboardServiceImpl implements DashboardService {

    /** 燃尽图时间窗天数（含今天）。与前端横轴刻度对齐，改动需同步度量页 */
    private static final int BURNDOWN_WINDOW_DAYS = 14;

    /** round2 的取整倍数（保留两位小数） */
    private static final double ROUND_SCALE = 100.0;

    /** 类型码 → 展示名，同时决定度量页卡片的顺序 */
    private static final Map<String, String> TYPE_NAMES = new LinkedHashMap<>();

    static {
        // 键是分类型概览 SQL 里打上的 type_code（与 ItemCategory 枚举名一致）
        TYPE_NAMES.put(ItemCategory.REQUIREMENT.name(), "需求");
        TYPE_NAMES.put(ItemCategory.BUG.name(), "缺陷");
        TYPE_NAMES.put(ItemCategory.TASK.name(), "任务");
    }

    private final DashboardMapper dashboardMapper;

    /** 燃尽图某一天的增量点：date 为 yyyy-MM-dd，value 为当日的创建/完成条数 */
    private record DailyPoint(String date, long value) {
    }

    private DashboardVO.TypeSummary emptyTypeSummary(String typeCode) {
        DashboardVO.TypeSummary ts = new DashboardVO.TypeSummary();
        ts.setTypeCode(typeCode);
        ts.setTypeName(TYPE_NAMES.get(typeCode));
        return ts;
    }

    @Override
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
        LocalDate startDate = end.minusDays(BURNDOWN_WINDOW_DAYS - 1L);
        LocalDateTime windowStart = startDate.atStartOfDay();

        long cumCreated = dashboardMapper.countCreatedBefore(projectId, windowStart);
        long cumDone = dashboardMapper.countDoneBefore(projectId, windowStart);

        List<DailyPoint> createdByDate = createdByDate(dashboardMapper.dailyCreated(projectId, windowStart));
        List<DailyPoint> completedByDate = completedByDate(dashboardMapper.dailyCompleted(projectId, windowStart));

        List<DashboardVO.BurndownPoint> points = new ArrayList<>();
        for (int d = 0; d < BURNDOWN_WINDOW_DAYS; d++) {
            String key = startDate.plusDays(d).toString();
            cumCreated += valueOf(createdByDate, key);
            cumDone += valueOf(completedByDate, key);
            DashboardVO.BurndownPoint p = new DashboardVO.BurndownPoint();
            p.setDate(key);
            p.setRemaining(Math.max(0, cumCreated - cumDone));
            p.setCompleted(cumDone);
            points.add(p);
        }
        vo.setBurndown(points);
    }

    /**
     * 按日期汇总每日新建数。
     *
     * <p>返回 {@code List<DailyPoint>} 而不是 {@code Map<String, Long>}：
     * 方法签名不返回裸容器，调用方只需要「按日期取增量」这一种语义，见 {@link #valueOf}。
     */
    private List<DailyPoint> createdByDate(List<DailyCreatedRow> rows) {
        Map<String, Long> daily = new LinkedHashMap<>();
        for (DailyCreatedRow row : rows) {
            daily.merge(row.getDate(), orZero(row.getCreated()), Long::sum);
        }
        return toPoints(daily);
    }

    /** 按日期汇总每日完成数，返回语义同 {@link #createdByDate} */
    private List<DailyPoint> completedByDate(List<DailyCompletedRow> rows) {
        Map<String, Long> daily = new LinkedHashMap<>();
        for (DailyCompletedRow row : rows) {
            daily.merge(row.getDate(), orZero(row.getCompleted()), Long::sum);
        }
        return toPoints(daily);
    }

    /** 局部 Map 累积 → 有类型的点列表（Map 只作为累加的工作区，不作为返回类型） */
    private List<DailyPoint> toPoints(Map<String, Long> daily) {
        List<DailyPoint> points = new ArrayList<>(daily.size());
        daily.forEach((date, value) -> points.add(new DailyPoint(date, value)));
        return points;
    }

    /** 取某一天的增量；SQL 只返回有数据的日期，缺失按 0 处理 */
    private long valueOf(List<DailyPoint> points, String date) {
        for (DailyPoint point : points) {
            if (point.date().equals(date)) {
                return point.value();
            }
        }
        return 0L;
    }

    /** 打包方法：SQL 侧的 COUNT/AVG 理论上不会为 null，但显式兜底避免拆箱 NPE */
    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round2(double v) {
        return Math.round(v * ROUND_SCALE) / ROUND_SCALE;
    }
}
