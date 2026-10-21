package com.seven7m.kanban.dto.dashboard;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目统计报表视图（只读聚合，对应设计文档「统计报表」章节）。
 */
@Data
public class DashboardVO {

    /** 概览卡片：总数 / 待处理 / 处理中 / 已完成 / 逾期 */
    private Summary summary = new Summary();

    /**
     * 分类型概览（需求 / 缺陷 / 任务），供度量页按类型分组展示。
     * 顺序固定为 需求、缺陷、任务，前端直接按顺序渲染。
     */
    private List<TypeSummary> typeSummaries = new ArrayList<>();

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
        /** 未开始（issue_status.category = TODO） */
        private long todo;
        /** 进行中（category = IN_PROGRESS） */
        private long inProgress;
        /** 已完成（category = DONE，含已关闭/已取消等终态） */
        private long done;
        /** 逾期：有截止日、已过期且未完成 */
        private long overdue;
    }

    /** 单类型概览：与 Summary 同口径，只是限定在 requirement / bug / task 一张表上 */
    @Data
    public static class TypeSummary {
        /** REQUIREMENT / BUG / TASK，前端据此决定跳转到哪个列表页 */
        private String typeCode;
        private String typeName;
        private long total;
        private long todo;
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
