package com.seven7m.kanban.dto.board;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BoardView {

    private UUID id;
    private UUID projectId;
    private String name;
    private List<BoardColumnView> columns;
}
