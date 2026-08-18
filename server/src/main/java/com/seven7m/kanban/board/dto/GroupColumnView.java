package com.seven7m.kanban.board.dto;

import com.seven7m.kanban.issue.dto.IssueVO;
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
}
