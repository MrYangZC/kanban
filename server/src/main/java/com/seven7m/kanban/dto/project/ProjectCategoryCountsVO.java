package com.seven7m.kanban.dto.project;

import lombok.Data;

/**
 * 项目分类计数 VO，用于分类标签栏计数展示。
 * 满足包含关系：managed ≤ participated ≤ all，archived 与 all 互斥。
 */
@Data
public class ProjectCategoryCountsVO {

    private long all;
    private long managed;
    private long participated;
    private long favorited;
    private long archived;
}