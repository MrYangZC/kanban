package com.seven7m.kanban.entity.workitem;

import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.enums.ItemCategory;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务（物理表 task）。
 */
@Getter
@Setter
@TableName("task")
public class Task extends AbstractWorkItem {

    /** 任务迭代 */
    private String iteration;

    @Override
    public ItemCategory category() {
        return ItemCategory.TASK;
    }
}
