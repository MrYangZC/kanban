package com.seven7m.kanban.dto.board;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分组（Swimlane）视图：按 groupBy 维度（状态/负责人/优先级）切割出的横向区块，
 * 每个区块内部仍是状态列布局。对应设计文档「筛选与分组」章节。
 */
@Data
public class BoardGroupView {

    /** 看板名称 */
    private String name;

    /** 分组维度：STATUS / ASSIGNEE / PRIORITY / SPRINT */
    private String groupBy;

    /** 分组条目列表 */
    private List<GroupItem> groups = new ArrayList<>();

    @Data
    public static class GroupItem {
        private String groupKey;
        private String groupName;
        private long issueCount;
        private List<GroupColumnView> columns = new ArrayList<>();
    }
}
