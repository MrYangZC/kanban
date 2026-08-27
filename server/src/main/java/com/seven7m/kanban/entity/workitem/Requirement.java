package com.seven7m.kanban.entity.workitem;

import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.enums.ItemCategory;
import lombok.Getter;
import lombok.Setter;

/**
 * 需求（物理表 requirement）。
 */
@Getter
@Setter
@TableName("requirement")
public class Requirement extends AbstractWorkItem {

    /** 验收标准 */
    private String acceptanceCriteria;
    /** 归属模块 */
    private String module;

    @Override
    public ItemCategory category() {
        return ItemCategory.REQUIREMENT;
    }
}
