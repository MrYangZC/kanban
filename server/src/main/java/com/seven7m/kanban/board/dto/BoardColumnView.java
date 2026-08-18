package com.seven7m.kanban.board.dto;

import com.seven7m.kanban.issue.dto.IssueVO;
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
