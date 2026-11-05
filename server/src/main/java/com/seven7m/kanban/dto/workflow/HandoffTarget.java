package com.seven7m.kanban.dto.workflow;

import java.util.UUID;

/**
 * 交接解析结果：进入某一状态列后应当交给谁、顺手落到哪里。
 *
 * @param userId     目标负责人
 * @param userName   显示名，通知与动态直接使用，避免调用方再查一次用户
 * @param note       这一步要做什么，可为空
 * @param sprintId   落点迭代，可空；非空时交接会把工作项放进这个迭代
 * @param sprintName 迭代名，写动态用
 * @param labelId    落点标签，可空；非空时交接会给工作项补上这个标签
 * @param labelName  标签名，写动态用
 */
public record HandoffTarget(UUID userId, String userName, String note,
                            UUID sprintId, String sprintName,
                            UUID labelId, String labelName) {
}
