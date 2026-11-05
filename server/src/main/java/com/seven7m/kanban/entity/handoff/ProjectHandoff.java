package com.seven7m.kanban.entity.handoff;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目级工作流交接配置：进入 status_id 这一列后，负责人转交给谁。
 *
 * <p>按 (project_id, category, status_id) 唯一，语义等价于 Multica「仅当前项目生效时
 * 自动复制一份工作流」，但只花一张表。handoff_type = NONE 的记录可保留可删除，
 * 读取侧一律按「无有效配置」处理。
 *
 * <p>除了换负责人，还可以顺带规定落点：{@code target_sprint_id} 把工作项放进指定迭代，
 * {@code target_label_id} 给它打上指定标签。两者都可空，空即「只换人」。
 * 落点只做单向写入（交接时设置），撤销时只回退迭代、不删标签 —— 标签是可累加的，
 * 撤销交接那一刻它可能已经被别人手工加过或本来就有，删掉就是覆盖别人的编辑。
 *
 * <p>交接只产生这几个副作用：改负责人 / 改迭代 / 加标签、写动态、发站内通知。
 * 本项目不接入 AI Agent 执行体，因此不做「交接后自动起跑」和「完成后自动移到 /
 * 驳回退回」那套（那是写给 agent 的指令），状态推进仍由 workflow_transition 约束、由人操作。
 * 误交接可由 {@code POST /v1/issues/{id}/handoff/revoke} 退回原负责人，取代 agent 场景里的「取消 run」。
 */
@Getter
@Setter
@TableName("project_handoff")
public class ProjectHandoff {

    /**
     * 主键必须由调用方显式赋值（IdType.INPUT）。
     * 不能用 ASSIGN_UUID：它生成 32 位字符串，反射写入 UUID 字段会抛
     * 「Could not set property 'id' ... argument type mismatch」，接口表现为 500。
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID projectId;

    /** 工作项大类：TASK / REQUIREMENT / BUG */
    private String category;

    private UUID statusId;

    /** NONE / MEMBER / PROJECT_LEAD，见 HandoffType */
    private String handoffType;

    /** handoffType = MEMBER 时必填 */
    private UUID handoffUserId;

    /** 这一步要做什么，供接手人参考；不参与任何校验 */
    private String handoffNote;

    /** 落点迭代（可空）；交接时把工作项的 sprint_id 改成它 */
    private UUID targetSprintId;

    /** 落点标签（可空）；交接时给工作项补上这个标签 */
    private UUID targetLabelId;

    private UUID updatedBy;

    private LocalDateTime updatedAt;
}
