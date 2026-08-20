package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.util.UUID;

@Data
public class LabelVO {
    private UUID id;
    private String name;
    private String color;
}
