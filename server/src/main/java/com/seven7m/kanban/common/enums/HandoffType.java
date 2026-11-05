package com.seven7m.kanban.common.enums;

/**
 * 工作流列的交接类型：工作项进入该状态列后，负责人是否被转交。
 *
 * <p>对应 project_handoff.handoff_type。默认 {@link #NONE}，即不交接、保持原负责人，
 * 保证能力上线后对存量项目零影响。
 *
 * <p><b>边界（对照 Multica 后确定，不要再扩）：</b>Multica 的 handoff 除了改负责人，
 * 还会在目标为 agent 时立刻创建一个 run 自动开跑，并给该步骤带上「完成后移到 /
 * 驳回退回」的指令。我们没有人机执行体，所以唯一合法语义就是「这个人接手」——
 * 因此这里不预留 AGENT 枚举、表里也不预留 agent_id：真要接入时在 {@link #fromCode}
 * 的 switch 与 HandoffResolver 里各加一个分支即可，提前埋列只会留下无人读取的语义。
 * 与之配套，agent 场景的「取消 run」在本项目对应 {@code /issues/{id}/handoff/revoke}。
 */
public enum HandoffType {

    /** 不交接，保留原负责人 */
    NONE,

    /** 交给指定成员，此时 handoff_user_id 必填 */
    MEMBER,

    /** 交给项目负责人，取 project.lead_user_id */
    PROJECT_LEAD;

    /**
     * 宽松解析：库里理论上有 CHECK 约束兜底，但历史数据/手工改库可能出现非法值，
     * 一律回落 NONE 而不是抛异常，避免一条脏配置把整块看板打挂。
     */
    public static HandoffType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return NONE;
        }
        String trimmed = code.trim();
        for (HandoffType type : values()) {
            if (type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return NONE;
    }
}
