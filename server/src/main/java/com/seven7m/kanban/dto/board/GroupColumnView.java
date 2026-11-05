package com.seven7m.kanban.dto.board;

import com.seven7m.kanban.dto.issue.IssueVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 分组内单个状态列的视图（复用看板列布局）。
 */
@Data
public class GroupColumnView {
    private UUID statusId;
    private String name;
    private String category;
    private String color;
    private Integer sortOrder;
    private List<IssueVO> issues = new ArrayList<>();

    /** 进入本列后是否交接：NONE / MEMBER / PROJECT_LEAD */
    private String handoffType = "NONE";
    /** 解析后的目标处理人（PROJECT_LEAD 时已替换为项目负责人） */
    private UUID handoffUserId;
    private String handoffUserName;
    /** 这一步要做什么 */
    private String handoffNote;

    /** 落点迭代：交接时把工作项放进这个迭代，可空 */
    private UUID targetSprintId;
    private String targetSprintName;

    /** 落点标签：交接时给工作项补上这个标签，可空 */
    private UUID targetLabelId;
    private String targetLabelName;
}
