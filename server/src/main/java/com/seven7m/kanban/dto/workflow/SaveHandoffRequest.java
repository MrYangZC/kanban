package com.seven7m.kanban.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 保存某一列的交接配置。handoffType = NONE 表示清除交接（保留原负责人）。
 *
 * <p>targetSprintId / targetLabelId 是「落点」：交接时顺带把工作项放进这个迭代、打上这个标签。
 * 两者都可空，空即只换人；类型为 NONE 时一律被忽略并清空。
 *
 * <p>校验由控制器 `@Valid` 触发；类型取值与「MEMBER 必须给负责人」的联动校验放在 Service
 * （跨字段规则 Bean Validation 表达不清晰）。
 */
@Data
public class SaveHandoffRequest {

    /** NONE / MEMBER / PROJECT_LEAD，必填 */
    @NotBlank(message = "交接类型不能为空")
    @Size(max = 20, message = "交接类型长度不能超过 20")
    private String handoffType;

    /** handoffType = MEMBER 时必填 */
    private UUID handoffUserId;

    /** 这一步要做什么，最长 500 字 */
    @Size(max = 500, message = "交接说明不能超过 500 字")
    private String handoffNote;

    /** 落点迭代，必须是本项目下的迭代 */
    private UUID targetSprintId;

    /** 落点标签，必须是本项目已选用的标签 */
    private UUID targetLabelId;
}
