package com.seven7m.kanban.dto.workflow;

import java.util.UUID;

/**
 * 工作流的一列（状态）+ 该列的交接配置。看板列头与详情页状态下拉共用同一份数据，
 * 避免两边各拼一套「进入后交给谁」的文案。
 *
 * @param handoffType       NONE / MEMBER / PROJECT_LEAD
 * @param handoffUserId     解析后的目标处理人；PROJECT_LEAD 时已替换为项目负责人
 * @param handoffUserName   目标处理人显示名，前端直接用，省一次用户查询
 * @param handoffNote       这一步要做什么
 * @param targetSprintId    落点迭代，可空；交接时把工作项放进这个迭代
 * @param targetSprintName  迭代名，可空
 * @param targetLabelId     落点标签，可空；交接时给工作项补上这个标签
 * @param targetLabelName   标签名，可空
 */
public record WorkflowColumnVO(
        UUID statusId,
        String name,
        String category,
        String color,
        Integer sortOrder,
        String handoffType,
        UUID handoffUserId,
        String handoffUserName,
        String handoffNote,
        UUID targetSprintId,
        String targetSprintName,
        UUID targetLabelId,
        String targetLabelName
) {
}
