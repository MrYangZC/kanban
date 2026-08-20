package com.seven7m.kanban.dto.project;

import lombok.Data;

import java.util.UUID;

/** 项目卡片统计信息（成员数 / 任务数 / 附件数）。 */
@Data
public class ProjectStats {

    private UUID projectId;
    private long memberCount;
    private long issueCount;
    private long attachmentCount;
}
