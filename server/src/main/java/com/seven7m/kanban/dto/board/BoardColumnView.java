package com.seven7m.kanban.dto.board;

import com.seven7m.kanban.dto.issue.IssueVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class BoardColumnView {

    private UUID id;
    private UUID statusId;
    private String name;
    private String category;
    private String color;
    private Integer sortOrder;
    private Integer wipLimit;
    private List<IssueVO> issues = new ArrayList<>();
}
