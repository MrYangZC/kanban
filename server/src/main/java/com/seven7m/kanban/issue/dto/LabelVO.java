package com.seven7m.kanban.issue.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class LabelVO {
    private UUID id;
    private String name;
    private String color;
}
