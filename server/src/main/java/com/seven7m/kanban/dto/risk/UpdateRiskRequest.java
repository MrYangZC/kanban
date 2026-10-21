package com.seven7m.kanban.dto.risk;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateRiskRequest {
    @Size(min = 1, message = "风险标题不能为空")
    private String title;
    private String description;
    private String level;
    private String status;
    private UUID ownerId;
    private LocalDate dueDate;
    /**
     * 需要置空的字段名（ownerId / dueDate）。
     * 本接口的 null 一律表示「不修改」，想把负责人或截止日清掉必须显式列在这里，
     * 与工作项更新的 clearFields 语义保持一致。
     */
    private List<String> clearFields;
}
