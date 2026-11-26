package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 新建工作项请求。
 * 枚举与长度约束与前端 issueDict 及数据库列宽保持一致：
 * priority P0~P3（priority varchar(20)）、severity S1~S4（severity varchar(20)）、
 * title varchar(500)。缺约束时超长/非法值会直接触发数据库异常返回 500。
 */
@Data
public class CreateIssueRequest {

    @NotNull
    private UUID projectId;

    private UUID issueTypeId;

    /** 工作项类型编码（如 STORY/TASK/BUG），优先于 issueTypeId 解析 */
    @Size(max = 64, message = "类型编码长度不能超过 64")
    private String issueTypeCode;

    @NotNull
    private UUID statusId;

    private UUID sprintId;

    @NotBlank
    @Size(max = 500, message = "标题长度不能超过 500")
    private String title;

    private String description;

    @Pattern(regexp = "^P[0-3]$", message = "优先级仅支持 P0~P3")
    private String priority;

    private UUID assigneeId;
    private UUID verifierId;
    /** 父工作项：支持直接建子任务（此前只能靠 Excel 导入设置层级） */
    private UUID parentId;
    /** 实际完成时间：不传则按状态自动取当前时间（导入历史数据时需要显式指定） */
    private LocalDate doneAt;
    private LocalDate dueDate;

    @Pattern(regexp = "^S[1-4]$", message = "严重度仅支持 S1~S4")
    private String severity;

    @Size(max = 100, message = "模块长度不能超过 100")
    private String module;

    @Size(max = 60, message = "修复版本长度不能超过 60")
    private String fixVersion;

    @Size(max = 60, message = "来源长度不能超过 60")
    private String source;

    private Boolean archived;
}
