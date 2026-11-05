package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.util.List;

/**
 * 工作项时间线（评论 + 动态）的一页数据。
 *
 * <p>比通用 PageResult 多带一个 {@code commentCount}：评论页签的角标需要"评论总数"，
 * 而分页后前端手里只有当前页，无法自行统计；顺带避免为了角标再发一次请求。
 */
@Data
public class IssueTimelineVO {

    /** 评论 + 动态总条数 */
    private long total;

    /** 其中评论条数（用于「评论·N」角标） */
    private long commentCount;

    private int page;
    private int size;

    /** 当前页数据，按时间倒序（最新在前） */
    private List<IssueActivityVO> list;
}
