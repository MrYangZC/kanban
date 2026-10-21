package com.seven7m.kanban.dto.dashboard;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目统计报表视图（只读聚合，对应设计文档「统计报表」章节）。
 */
@Data
public class DashboardVO {

    /** 概览卡片：总数 / 进行中 / 已完成 / 逾期 */
    private Summary summary = new Summary();

    /** 状态分布（按 issue_status 聚合） */
    private List<NameCount> statusDist = new ArrayList<>();

    /** 优先级分布（P0~P3） */
    private List<NameCount> priorityDist = new ArrayList<>();

    /** 按负责人分布的工作量（带显示名与头像，度量页按人展示） */
    private List<AssigneeCount> assigneeDist = new ArrayList<>();

    /** 周期时间：已完成 Issue 的平均/最长解决时长（小时） */
    private CycleTime cycleTime = new CycleTime();

    /** 燃尽图：按日剩余/完成趋势 */
    private List<BurndownPoint> burndown = new ArrayList<>();

    @Data
    public static class Summary {
        private long total;
        private long inProgress;
        private long done;
        private long overdue;
    }

    @Data
    public static class NameCount {
        private String name;
        private long count;
    }

    /**
     * 负责人维度计数。name 是展示用名字（display_name 优先，兜底 username，再兜底「未分配」），
     * avatarUrl 为空时前端回退首字/图标，未分配行不带头像。
     */
    @Data
    public static class AssigneeCount {
        private String name;
        private long count;
        private String avatarUrl;
    }

    @Data
    public static class CycleTime {
        private double avgHours;
        private double maxHours;
    }

    @Data
    public static class BurndownPoint {
        private String date;
        private long remaining;
        private long completed;
    }
}
