package com.seven7m.kanban.dto.dev;

import java.time.LocalDate;

/**
 * 解析后的单行任务模型。
 * 结构化 sheet 与大纲 sheet 统一为同一模型，缺失字段为 null。
 */
public record ExcelRow(
        /** 任务标题（必填） */
        String title,
        /** Excel 任务编号，如 T002，用于父子反查；大纲 sheet 为 null */
        String issueNo,
        /** 类别 */
        String category,
        /** 负责人中文姓名（可能含逗号组合的多个） */
        String assigneeRaw,
        /** 优先级 P1-P4 */
        String priority,
        /** 计划完成时间 */
        LocalDate dueDate,
        /** 实际完成时间（拼入描述，系统无该字段） */
        LocalDate actualDate,
        /** 原始状态文本：待办/进行中/已完成 */
        String statusText,
        /** 备注 */
        String remark,
        /** 父任务编号（本 sheet 内），用于反查 parentId */
        String parentNo
) {
}
