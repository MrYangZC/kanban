package com.seven7m.kanban.entity.workitem;

import com.baomidou.mybatisplus.annotation.TableName;
import com.seven7m.kanban.common.enums.ItemCategory;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 缺陷（物理表 bug）。
 */
@Getter
@Setter
@TableName("bug")
public class Bug extends AbstractWorkItem {

    private String severity;
    private String module;
    private String fixVersion;
    private String source;
    private String iteration;
    private LocalDate planStart;
    /** 不修复理由 */
    private String noFixReason;

    @Override
    public ItemCategory category() {
        return ItemCategory.BUG;
    }
}
