package com.seven7m.kanban.mapper.issue.comment;

import lombok.Data;

import java.util.UUID;

/**
 * 「工作项 → 评论数」的查询投影（批量统计用）。
 *
 * <p>此前该查询直接返回 {@code List<Map<String, Object>>}，调用方按字符串键取值；
 * 按《阿里巴巴 Java 开发手册》ORM 规约，查询结果必须由 POJO 承载并显式映射，
 * 避免列别名写错时字段静默为 null。映射声明见 {@link IssueCommentMapper#countByIssueIds}。
 */
@Data
public class CommentCountRow {

    private UUID issueId;

    /** 该工作项的评论条数（SQL 别名 cnt） */
    private Long cnt;
}
