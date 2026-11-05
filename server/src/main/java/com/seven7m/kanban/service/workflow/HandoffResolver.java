package com.seven7m.kanban.service.workflow;

import com.seven7m.kanban.common.enums.HandoffType;
import com.seven7m.kanban.entity.handoff.ProjectHandoff;
import com.seven7m.kanban.entity.project.Project;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 交接人解析：把「配置」翻译成「最终负责人」。
 *
 * <p>与 WorkflowService 拆开的理由是这个换算规则要同时被「看板列头展示」和
 * 「状态流转时改负责人」两处使用，后者在事务内，不该为了解析再穿一层查询。
 */
@Component
public class HandoffResolver {

    /**
     * @return 目标负责人；无配置、类型为 NONE、或解析不出人（如项目没有负责人）时返回 null，
     *         调用方一律按「不交接」处理。
     */
    public UUID resolve(ProjectHandoff config, Project project) {
        if (config == null) {
            return null;
        }
        return switch (HandoffType.fromCode(config.getHandoffType())) {
            case MEMBER -> config.getHandoffUserId();
            case PROJECT_LEAD -> project == null ? null : project.getLeadUserId();
            case NONE -> null;
        };
    }
}
